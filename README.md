# cc-assay-chain

管理矿样收样、分样、交接和检测结果。

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

### 4. 检测结果

- 只有当前持有该叶子样本的实验室能提交结果；结果使用固定精度 `DECIMAL(19,6)`。
- 每个（样本，检测项目）最多一个有效结果，由唯一约束 `uk_assay_sample_item` 保护。
- 结果事件不可覆盖；重复提交同一项目返回 409。
- 事件号同样遵循「相同内容幂等、不同内容冲突」。

### 5. 并发与一致性

- 样本带 JPA 乐观锁（`@Version`）和待交接指针，唯一约束兜底：
  并发分样/交接/结果提交下不会出现双重保管、父子同时有效或重复检测。
- 所有写入均为单一事务，乐观锁失败或唯一约束冲突时整体回滚。

### 6. 谱系查询

- 可从任意样本向上查询祖先链、向下查询全部后代，并汇总谱系内所有样本的
  接收（RECEIVE）、分样（SPLIT）、交接（CUSTODY/CUSTODY_CONFIRM）、检测（ASSAY）事件时间线。

## HTTP 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/samples` | 接收原始矿样 |
| GET  | `/api/samples/{externalNo}` | 查询样本当前质量与保管方 |
| POST | `/api/splits` | 层级分样 |
| POST | `/api/custody/initiate` | 当前保管方发起交接 |
| POST | `/api/custody/confirm` | 指定实验室确认交接 |
| POST | `/api/assays` | 提交检测结果 |
| GET  | `/api/samples/{externalNo}/lineage` | 查询完整谱系与事件时间线 |

错误状态码：参数/业务规则不合法 `400`，样本或事件不存在 `404`，
事件号内容冲突、唯一约束冲突、并发竞争 `409`。

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

提交结果：

```json
POST /api/assays
{"eventNo":"AS-001","sampleExternalNo":"ORE-001-A","itemCode":"AU_GRADE",
 "resultValue":3.250000,"unit":"g/t","submittedBy":"中心实验室"}
```
