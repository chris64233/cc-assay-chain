# cc-assay-chain

管理矿样收样、分样、交接、检测结果复核与更正。

## 开发环境

- JDK 21
- Spring Boot 4.1.1
- Maven Wrapper 3.9.9
- H2

## 本地运行

启动服务：

    ./mvnw spring-boot:run

运行测试：

    ./mvnw clean test

## 主要业务规则

### 1. 矿样接收

- 接收原始矿样时记录唯一外部样本号、矿区、质量（克）和当前保管方。
- 质量使用 `BigDecimal` 固定精度 `DECIMAL(19,4)`，必须为正数，超过 4 位小数直接拒绝。
- 外部样本号由数据库唯一约束 `uk_sample_external_no` 保护，重复接收返回 409。

### 2. 层级分样

- 一次分样从一个叶子样本产生至少 2 个子样本，并声明处理损耗。
- 质量守恒：`子样本质量之和 + 声明损耗` 必须等于分样前质量；允许的严格舍入误差为
  `0.0001` 克（一个最小刻度，见 `MassRules.MASS_TOLERANCE`），超出即拒绝。
- 记账损耗按闭差保存：`账面损耗 = 分样前质量 - 子样本质量之和`，保证库内账目严格守恒。
- 分样是原子事务：事件、全部子样本和父样本叶子标记一起提交，失败回滚，不留部分谱系。
- 已分样的父样本变为非叶子样本，不能再次分样、交接或提交检测结果。

### 3. 实验室交接

- 叶子样本可从当前保管方交接给指定实验室，交接分两步：发起（PENDING）→ 确认（CONFIRMED）。
- 只有当前保管方能发起，只有指定的接收实验室能确认。
- 确认时保管方原子切换，交接事件不可变；同一时间只允许一个待确认交接。
- 事件号幂等：相同事件号 + 相同内容重复提交视为重放，返回原事件；内容不同返回 409。

### 4. 检测结果提交与复核

- 只有当前持有该叶子样本的实验室能提交结果；结果使用固定精度 `DECIMAL(19,6)`。
- 实验室提交的结果先进入 **待复核（PENDING_REVIEW）**，不对外有效。
- 复核人必须与原提交人不同；复核通过（APPROVE）后结果才成为 **生效（EFFECTIVE）**
  的对外有效结果；复核驳回（REJECT）后结果不生效，该项目可重新提交。
- 只能对当前保管链完整（无待确认交接）、尚未作废（仍为叶子）的样本作出复核决定；
  样本在复核期间被分样或处于待确认交接时，复核返回 400。
- 待复核期间不允许重复提交同一项目；结果号（外部结果号）遵循
  「相同内容幂等、不同内容冲突（409）」。

### 5. 结果更正与版本链

- 已生效结果 **不得直接覆盖**。发现仪器、单位或录入错误时，必须创建引用原结果的
  **更正申请**，完整保存旧值、新值、原因和证据（以及申请人）。
- 只有生效结果可以发起更正；新旧数值与单位完全一致时拒绝；
- 一个生效结果同时只允许一个待审批更正申请。
- 更正审批人必须与更正申请人不同：
  - 批准：原版本置为 **被取代（SUPERSEDED）**，按版本链生成版本号递增的新版本
    （立即生效，结果号为 `COR-{更正号}`），审批事件记录新版本结果号；
  - 驳回：更正申请置为 REJECTED，原结果保持有效。
- 所有旧版本与审批记录均保留：版本链、生效时间（`effective_at`）支持还原
  **任意时点的有效结果**（历史时点查询）。
- **并发安全（防旧版本覆盖新版本）**：更正批准时重新校验申请引用的原版本
  仍是当前生效版本；若批准落库前已有另一更先生效（原版本已被取代），
  本次审批直接返回 409，不会覆盖新版本。
- 更正号、审批事件号分别保证幂等：同号同内容重放返回原记录，同号异内容返回 409。

### 6. 并发与一致性

- 样本带 JPA 乐观锁（`@Version`）和待交接指针；
  提交结果、复核、更正申请/审批、分样、交接在事务内先对样本行加
  **悲观写锁**（`PESSIMISTIC_WRITE`，见 `SampleRepository.findLockedBy*`），
  将同一样本上的这些写操作严格串行化。
- 因此「更正审批 / 结果发布」与「继续分样 / 保管方变更」并发时：
  - 分样先行 → 样本已非叶子，任何结果发布、复核、更正均被拒绝，
    不会对非叶子样本发布新结果；
  - 结果事务先行 → 分样随后看到最新叶子状态后成功，结果保留在分样前窗口。
- 唯一约束（结果号、更正号、审批事件号、样本号）兜底，乐观锁失败、
  唯一约束冲突或锁竞争均整体回滚并返回 409。

### 7. 谱系与结果联合查询

- 可从任意样本向上查询祖先链、向下查询全部后代，并汇总谱系内所有样本的
  接收（RECEIVE）、分样（SPLIT）、交接（CUSTODY/CUSTODY_CONFIRM）、
  检测提交（ASSAY）、更正申请（CORRECTION）、复核与更正审批
  （ASSAY_REVIEW/CORRECTION_DECISION）事件时间线。
- 针对（样本，检测项目）提供联合查询：当前有效结果、当前待复核结果、
  完整版本链（每个版本可追溯来源更正号）、全部复核/审批记录，
  并可用 `asOf` 参数还原任意时点的有效结果。
- 样本级联合查询同时返回完整谱系与该样本每个检测项目的结果信息。

## HTTP 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/samples` | 接收原始矿样 |
| GET  | `/api/samples/{externalNo}` | 查询样本当前质量与保管方 |
| POST | `/api/splits` | 层级分样 |
| POST | `/api/custody/initiate` | 当前保管方发起交接 |
| POST | `/api/custody/confirm` | 指定实验室确认交接 |
| POST | `/api/assays` | 提交检测结果（进入待复核） |
| POST | `/api/assays/reviews` | 复核待复核结果 |
| POST | `/api/corrections` | 创建结果更正申请 |
| POST | `/api/corrections/decisions` | 审批更正申请 |
| GET  | `/api/samples/{externalNo}/results/{itemCode}/effective` | 查询当前有效结果 |
| GET  | `/api/samples/{externalNo}/results/{itemCode}?asOf=` | 版本链+复核记录，可还原历史时点 |
| GET  | `/api/samples/{externalNo}/results` | 样本谱系与全部项目结果联合查询 |
| GET  | `/api/samples/{externalNo}/lineage` | 查询完整谱系与事件时间线 |

错误状态码：参数/业务规则不合法 `400`，样本/结果/事件不存在 `404`，
事件号内容冲突、唯一约束冲突、并发竞争（锁竞争/旧版本覆盖）`409`。

### 请求示例

收样：

```json
POST /api/samples
{"externalNo":"ORE-001","miningArea":"甲玛矿区","mass":100.0000,"custodian":"地勘院"}
```

分样：

```json
POST /api/splits
{"eventNo":"SP-001","parentExternalNo":"ORE-001","declaredLossMass":10.0000,
 "children":[
   {"externalNo":"ORE-001-A","mass":60.0000,"custodian":"地勘院"},
   {"externalNo":"ORE-001-B","mass":30.0000,"custodian":"地勘院"}]}
```

交接与确认：

```json
POST /api/custody/initiate
{"eventNo":"CU-001","sampleExternalNo":"ORE-001-A","fromCustodian":"地勘院","toLab":"中心实验室"}

POST /api/custody/confirm
{"eventNo":"CU-001","confirmedBy":"中心实验室"}
```

提交结果与复核：

```json
POST /api/assays
{"eventNo":"AS-001","sampleExternalNo":"ORE-001-A","itemCode":"AU_GRADE",
 "resultValue":3.250000,"unit":"g/t","submittedBy":"中心实验室"}

POST /api/assays/reviews
{"approvalNo":"RV-001","resultEventNo":"AS-001",
 "decision":"APPROVE","decidedBy":"质量负责人","comment":"谱线复核无误"}
```

更正申请与审批：

```json
POST /api/corrections
{"correctionNo":"CR-001","resultEventNo":"AS-001","newValue":3.300000,"newUnit":"g/t",
 "reason":"仪器标定错误","evidence":"复检报告 LAB-2026-001","requestedBy":"中心实验室"}

POST /api/corrections/decisions
{"approvalNo":"AP-001","correctionNo":"CR-001",
 "decision":"APPROVE","decidedBy":"技术负责人","comment":"证据充分"}
```

批准后新版本结果号为 `COR-CR-001`，原结果 `AS-001` 置为 SUPERSEDED。

查询版本链并还原历史时点：

```
GET /api/samples/ORE-001-A/results/AU_GRADE
GET /api/samples/ORE-001-A/results/AU_GRADE?asOf=2026-09-01T00:00:00Z
```
