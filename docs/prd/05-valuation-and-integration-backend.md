<!-- generated-by: gsd-doc-writer -->

# PRD 5：组合估值、分析与后端集成模块

## 1. 分工信息

| 项目 | 内容 |
|---|---|
| 主负责人 | 成员 5（提交前替换为姓名） |
| 负责范围 | 估值 API、分析视图/快照、后端集成测试与 CI |
| 主要数据对象 | `portfolio_valuation_snapshot` 与分析视图 |
| 不负责 | 前端页面、交易写入和外部行情抓取 |
| 接口依据 | [API 接口说明](../API.md) / [OpenAPI 规范](../openapi.yaml) |
| 状态 | 待实现 |

## 2. 模块目标

把当前持仓和最新行情转换为市值、成本、盈亏、收益率、资产配置和历史趋势数据，同时整合四个后端模块的健康检查、MySQL 全量验证、契约测试和 CI。

## 3. 指标口径

- `marketValue = quantity × latestClosePrice`
- `costBasis = quantity × averageCost`
- `unrealizedPnl = marketValue - costBasis`
- `returnPct = unrealizedPnl ÷ costBasis × 100`
- `allocationPct = positionMarketValue ÷ portfolioPricedMarketValue × 100`

规则：

- Java 使用 `BigDecimal`，MySQL 使用 `DECIMAL`。
- 无价格持仓不进入已定价市值与配置分母。
- 返回总成本和已定价成本。
- 分母为零时返回 `null`。
- 中间值不提前按两位小数舍入。

## 4. 接口

| 方法 | 路径 | 成功状态 |
|---|---|---:|
| GET | `/api/v1/portfolios/{portfolioId}/dashboard` | 200 |
| GET | `/api/v1/portfolios/{portfolioId}/performance` | 200 |
| GET | `/health/live` | 200 |
| GET | `/health/ready` | 200/503 |

完整查询参数、响应字段和 examples 见 [API.md](../API.md)。

## 5. Dashboard 返回

必须一次返回：

- 组合基本信息。
- 总市值、总成本、已定价成本、未实现盈亏、收益率。
- 已定价和未定价持仓数量。
- 每个持仓的价格、日期、来源、状态和指标。
- 资产配置数组。
- 最新使用价格日期。

前端不应为了显示同一页面再组合多个金融计算接口。

## 6. Performance 返回

- 支持可选 `from` 和 `to` 日期。
- 按估值日期升序。
- 每个点包含市值、成本、盈亏和持仓计数。
- 无快照日期不补虚构数据。

## 7. MySQL 交付

- `portfolio_valuation_snapshot`
- `position_metrics`
- `portfolio_allocation`
- `portfolio_summary`
- 组合日期唯一键和历史查询索引

测试数据类型：

- 汇总金额 `DECIMAL(24,8)`。
- 百分比保持高精度。
- 时间为 UTC。

## 8. 快照流程

1. 接收成员 4 的同步完成事件。
2. 找出受影响组合。
3. 从最新价格与持仓读取一致数据。
4. 生成或更新当日快照。
5. 记录未定价持仓数。
6. 失败时记录错误，但不删除行情。

## 9. 后端与数据库测试

### 计算测试

- 正常、负盈亏、零成本、小数股和八位价格。
- 单资产、多资产和全部无行情。
- 配置比例和约等于 100%。

### API 测试

- dashboard 完整响应。
- performance 日期过滤与排序。
- 用户所有权和 404。
- ready 检查 MySQL 失败时返回 503。

### 数据库测试

- 三个分析视图与固定 fixture 结果。
- 同日快照唯一。
- 无行情持仓被计数但不进入分母。
- MySQL 8 全量 schema 和迁移从空库执行。

### 契约与 CI

- 使用 OpenAPI 校验所有后端实际响应。
- CI 运行四个模块单元与集成测试。
- CI 运行 MySQL 迁移、文档链接和秘密扫描。
- 发布候选运行完整后端冒烟测试。

## 10. OpenAPI 集成责任

- 检查四名后端成员的路由都出现在 `/v3/api-docs`。
- 检查所有成功与错误响应有 schema 和 examples。
- 比较运行时 schema 与 [openapi.yaml](../openapi.yaml)。
- 将契约差异作为 CI 失败处理。

## 11. 验收标准

### AC-VA-01：指标一致

固定持仓和价格下，MySQL 视图、API 与文档样例使用相同公式。

### AC-VA-02：无行情

无价格持仓被单独计数，不进入组合市值和配置分母。

### AC-VA-03：历史趋势

performance 按日期升序，缺失日期不补点。

### AC-VA-04：全量后端

空 MySQL 8 实例能够创建 8 张表、4 个视图和2个触发器，全部后端测试通过。

### AC-VA-05：接口文档

运行时 OpenAPI 与仓库规范无未批准差异。

## 12. 交接

- 从成员 2 获取组合所有权。
- 从成员 3 获取持仓与成本。
- 从成员 4 获取最新价格与同步事件。
- 向成员 1 提供 dashboard、performance 和 health examples。

## 13. 完成定义

- 四个接口和分析数据对象完成。
- 计算精度、无行情、快照与契约测试通过。
- CI 可以从空 MySQL 启动并验证全部后端。
