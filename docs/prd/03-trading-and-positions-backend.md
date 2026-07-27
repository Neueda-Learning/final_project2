<!-- generated-by: gsd-doc-writer -->

# PRD 3：标的、交易与持仓后端模块

## 1. 分工信息

| 项目 | 内容 |
|---|---|
| 主负责人 | 成员 3（提交前替换为姓名） |
| 负责范围 | 标的查询、交易/持仓 API、MySQL、后端测试 |
| 主要数据表 | `instrument`、`trade_transaction`、`portfolio_position` |
| 不负责 | 前端、行情同步和组合估值 |
| 接口依据 | [API 接口说明](../API.md) / [OpenAPI 规范](../openapi.yaml) |
| 状态 | 待实现 |

## 2. 模块目标

提供股票/ETF 搜索、买入卖出、交易历史和当前持仓接口；确保幂等、金融精度、超量卖出校验，以及交易和持仓投影的原子更新。

## 3. 接口

| 方法 | 路径 | 成功状态 |
|---|---|---:|
| GET | `/api/v1/instruments?query={text}` | 200 |
| POST | `/api/v1/portfolios/{portfolioId}/transactions` | 201 |
| GET | `/api/v1/portfolios/{portfolioId}/transactions` | 200 |
| GET | `/api/v1/portfolios/{portfolioId}/positions` | 200 |

请求字段和返回样例见 [API.md](../API.md)。成员 3 必须在 Spring MVC Controller 与 Springdoc 注解中同步配置相同 examples。

## 4. 交易规则

- 标的只允许 `STOCK` 和 `ETF`。
- 数量和成交价必须大于零，手续费不得小于零。
- MVP 只允许 long-only。
- 卖出数量不得超过当前持仓。
- 组合内幂等键唯一。
- Java 使用 `BigDecimal`，MySQL 使用 `DECIMAL`。
- 交易记录不可普通更新或删除，更正使用补偿交易。

## 5. 事务流程

1. 验证组合所有权与标的状态。
2. 检查组合和幂等键。
3. 使用 `SELECT ... FOR UPDATE` 锁定持仓。
4. 验证 SELL 数量。
5. 计算新数量、平均成本和已实现盈亏。
6. 插入不可变交易。
7. 新增或更新持仓投影和版本。
8. 在同一 MySQL 事务提交或整体回滚。

## 6. MySQL 交付

- `instrument`
- `trade_transaction`
- `portfolio_position`
- 幂等唯一键、复合主键、外键和查询索引
- 阻止交易 UPDATE/DELETE 的两个 `SIGNAL` 触发器

数据类型：

| 数据 | 类型 |
|---|---|
| UUID | `CHAR(36)` |
| 数量 | `DECIMAL(28,8)` |
| 价格/成本/费用 | `DECIMAL(20,8)` |
| 时间 | `DATETIME(6)` UTC |

## 7. 标准错误

| 错误码 | HTTP 状态 |
|---|---:|
| `INSTRUMENT_NOT_FOUND` | 404 |
| `INSTRUMENT_INACTIVE` | 409 |
| `POSITION_INSUFFICIENT_QUANTITY` | 409 |
| `IDEMPOTENCY_CONFLICT` | 409 |
| `CONCURRENT_POSITION_UPDATE` | 409 |
| `VALIDATION_ERROR` | 422 |

## 8. 后端测试

### 领域测试

- 首次买入、追加买入、部分卖出和全部卖出。
- 加权平均成本、手续费和已实现盈亏。
- 小数股和八位小数边界。
- 超量卖出与零/负输入。

### API 测试

- 四个接口的成功请求与错误响应。
- 分页、排序和稳定顺序。
- 幂等重放和相同键不同请求冲突。
- 组合所有权。

### 数据库/并发测试

- 负数量、零价格和重复幂等键被拒绝。
- 交易更新/删除被触发器拒绝。
- 并发买入不丢失更新。
- 交易失败时持仓同步回滚。

### OpenAPI 测试

- 每个接口包含请求参数、成功响应和错误 examples。
- 实际响应通过 OpenAPI schema 校验。

## 9. 验收标准

### AC-TR-01：原子买入

创建买入后，交易与持仓同时存在；任一步失败时两者都不保留。

### AC-TR-02：幂等

相同请求和幂等键提交两次，数据库只有一笔交易，持仓只增加一次。

### AC-TR-03：超量卖出

卖出超过持仓时返回 409 `POSITION_INSUFFICIENT_QUANTITY`，数据不变化。

### AC-TR-04：接口样例

Swagger UI 对交易请求显示完整 JSON body，并展示 201、409 和 422 响应样例。

## 10. 交接

- 依赖成员 2 的组合所有权校验。
- 向成员 1 提供标的、交易、持仓接口 examples。
- 向成员 4 提供活跃标的集合。
- 向成员 5 提供持仓数量与平均成本。

## 11. 完成定义

- 四个接口、三张表/迁移和测试通过。
- 事务、幂等和并发行为有数据库集成证据。
- API.md、OpenAPI 与实际响应一致。
