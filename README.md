# cc-assay-chain

管理矿样收样、分样、交接、检测结果复核与结果更正。

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
- 已分样的父样本变为非叶子样本，不能再次分样、交接、提交/被复核结果或批准更正。

### 3. 实验室交接

- 叶子样本可从当前保管方交接给指定实验室，交接分两步：发起（PENDING）→ 确认（CONFIRMED）。
- 只有当前保管方能发起，只有指定的接收实验室能确认。
- 确认时保管方原子切换，交接事件不可变；同一时间只允许一个待确认交接。
- 样本存在待确认交接期间视为「保管链不完整」，不能提交结果、复核结果或批准更正。
- 事件号幂等：相同事件号 + 相同内容重复提交视为重放，返回原事件；内容不同返回 409。

### 4. 检测结果提交与复核

- 只有当前持有该叶子样本、且无待确认交接（保管链完整）的实验室能提交结果；
  结果使用固定精度 `DECIMAL(19,6)`。
- 提交后的结果进入 **待复核 PENDING** 状态，尚不对外有效。
- 复核人必须与原结果提交人不同；只能对保管链完整、尚未作废的叶子样本作出复核决定。
- 复核通过（APPROVED）后结果成为 **生效 EFFECTIVE** 对外有效版本；
  复核驳回（REJECTED）后允许实验室重新提交，重新提交产生递增的新版本号。
- 待复核期间不能重复提交同一（样本，项目）；已生效结果不得直接覆盖，必须走更正流程。
- 结果号（`eventNo`）幂等：同号同内容重放返回原结果，同号异内容返回 409。

### 5. 结果更正与版本链

- 发现仪器、单位或录入错误时，对**当前生效版本**创建更正申请：
  服务端从原版本快照旧值与旧单位，并保存新值、新单位、原因和证据
  （`correction_request`，旧值不信任调用方）。
- 新值或新单位必须与原结果至少一项不同；同一结果版本同时只允许一个待审批更正。
- 更正号（`correctionNo`）幂等：同号同内容重放返回原申请，同号异内容返回 409。
- 更正审批人必须既不是原结果提交人、也不是更正申请人。
- 更正**批准**时在一个事务内：旧生效版本置为 SUPERSEDED（记录失效时间），
  并发布一个 EFFECTIVE 新版本（版本号 +1、`prevVersion` 指向旧版本，形成版本链）；
  驳回仅关闭申请，原结果继续有效。
- 所有历史版本都保留，不被删除；通过各版本的 `effectiveAt/supersededAt`
  可还原任意时点的有效结果（版本切换瞬间归属新版本）。
- 审批事件号（`eventNo`）全局幂等：结果复核与更正审批共用一套审批事件号，
  同号同内容重放返回原记录，同号异内容返回 409。

### 6. 并发与一致性

- 样本带 JPA 乐观锁（`@Version`）；结果提交、复核、更正审批、分样、交接在事务内
  按统一的「样本行优先」顺序申请 **悲观行锁**（`SELECT … FOR UPDATE`），
  再锁结果版本行/更正申请行。加锁查询均以标量投影先取主键，确保 FOR UPDATE
  是实体的首次加载，不会命中 JPA 一级缓存而被跳过。
- 因此：更正审批、样本继续分样、保管方变更并发时，系统会阻止对不再是叶子节点的
  样本发布新结果；基于旧结果版本的审批若发现原版本已被后来生效的版本取代，
  直接返回 409，不会覆盖新版本。
- 唯一约束兜底：`uk_assay_version`（样本+项目+版本号）、
  `uk_assay_effective`（仅生效行非空的 current_item_key，保证每项目至多一个生效版本）、
  `uk_correction_no`、`uk_correction_new_event_no`、`uk_review_event_no`。
- 所有写入均为单一事务，悲观锁冲突、乐观锁失败或唯一约束冲突时整体回滚。

### 7. 查询

- 当前对外有效结果：`GET /api/results/current`。
- 结果版本链 + 当前有效结果 + 复核记录联合查询：`GET /api/results/history`。
- 任意时点有效结果还原：`GET /api/results/effective-at?at=<ISO-8601>`。
- 样本谱系联合查询：`GET /api/samples/{externalNo}/lineage`，时间线除接收/分样/交接/
  检测外，还包含结果复核（RESULT_REVIEW）、更正申请（CORRECTION）与
  更正审批（CORRECTION_REVIEW）事件。

## HTTP 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/samples` | 接收原始矿样 |
| GET  | `/api/samples/{externalNo}` | 查询样本当前质量与保管方 |
| POST | `/api/splits` | 层级分样 |
| POST | `/api/custody/initiate` | 当前保管方发起交接 |
| POST | `/api/custody/confirm` | 指定实验室确认交接 |
| POST | `/api/assays` | 提交检测结果（进入待复核） |
| POST | `/api/results/review` | 复核检测结果（APPROVED/REJECTED） |
| POST | `/api/corrections` | 对生效结果发起更正申请 |
| POST | `/api/corrections/decide` | 审批更正申请（批准形成新版本） |
| GET  | `/api/results/current` | 查询当前有效结果 |
| GET  | `/api/results/history` | 查询结果版本链与复核记录 |
| GET  | `/api/results/effective-at` | 还原任意时点的有效结果 |
| GET  | `/api/samples/{externalNo}/lineage` | 查询完整谱系与事件时间线 |

错误状态码：参数/业务规则不合法 `400`，样本、结果或事件不存在 `404`，
号段内容冲突、唯一约束冲突、并发竞争 `409`。

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

提交结果（待复核）：

```json
POST /api/assays
{"eventNo":"AS-001","sampleExternalNo":"ORE-001-A","itemCode":"AU_GRADE",
 "resultValue":3.250000,"unit":"g/t","submittedBy":"中心实验室"}
```

复核（复核人必须不同于提交人；通过后结果才生效）：

```json
POST /api/results/review
{"eventNo":"RV-001","resultEventNo":"AS-001","decision":"APPROVED",
 "reviewedBy":"质量主管","comment":"结果合格"}
```

发起更正并审批（批准后形成 v2，旧值/新值/原因/证据全留痕）：

```json
POST /api/corrections
{"correctionNo":"CO-001","resultEventNo":"AS-001","newResultEventNo":"AS-002",
 "newResultValue":3.520000,"newUnit":"g/t",
 "reason":"仪器校准错误","evidence":"校准记录 CERT-77","requestedBy":"中心实验室"}

POST /api/corrections/decide
{"eventNo":"RV-002","correctionNo":"CO-001","decision":"APPROVED",
 "reviewedBy":"技术负责人","comment":"同意更正"}
```

查询：

```
GET /api/results/current?sampleExternalNo=ORE-001-A&itemCode=AU_GRADE
GET /api/results/history?sampleExternalNo=ORE-001-A&itemCode=AU_GRADE
GET /api/results/effective-at?sampleExternalNo=ORE-001-A&itemCode=AU_GRADE&at=2026-09-27T10:00:00Z
```
