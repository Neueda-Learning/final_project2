<!-- generated-by: gsd-doc-writer -->

# PRD 4：市场数据同步后端模块

## 1. 分工信息

| 项目 | 内容 |
|---|---|
| 主负责人 | 成员 4（提交前替换为姓名） |
| 负责范围 | 行情 provider、worker、同步 API、MySQL、后端测试 |
| 主要数据表 | `market_data_sync_run`、`market_price` |
| 不负责 | 前端和估值图表 |
| 接口依据 | [API 接口说明](../API.md) / [OpenAPI 规范](../openapi.yaml) |
| 状态 | 待实现 |

## 2. 模块目标

从真实外部提供者获取股票和 ETF 的最新日收盘价，幂等保存到 MySQL，并为前端和估值模块提供价格日期、来源、新鲜度及同步运行状态。

## 3. 行情口径

- MVP 是最新可用日收盘价，不是流式盘中行情。
- 首个真实 provider 为 Twelve Data REST API。
- provider 通过 Java `MarketDataProvider` 接口隔离。
- fixture provider 用于自动测试和离线演示。
- 外部失败时保留最后成功价格。

## 4. 接口

| 方法 | 路径 | 成功状态 |
|---|---|---:|
| POST | `/api/v1/market-data/sync` | 202 |
| GET | `/api/v1/market-data/sync-runs/latest` | 200 |
| GET | `/api/v1/instruments/{instrumentId}/latest-price` | 200 |

请求和响应 examples 见 [API.md](../API.md) 与 [openapi.yaml](../openapi.yaml)。

## 5. Provider 接口

```text
search_instruments(query, limit)
fetch_daily_closes(symbols, start_date, end_date)
health_check()
```

规范化价格包含：

- instrument ID 和 provider symbol
- price date
- open/high/low/close/adjusted close
- volume、currency 和 source
- source timestamp 与 fetched at

## 6. 同步流程

1. 使用 MySQL `GET_LOCK()` 获取全局命名锁。
2. 创建 `RUNNING` 同步记录。
3. 查询数量大于零的活跃持仓标的。
4. 分批调用 provider，并设置超时和有限重试。
5. 校验代码、日期、币种和正价格。
6. 按“标的 + 交易日 + 来源”执行 upsert。
7. 写入成功、失败和分类错误。
8. 更新为 `SUCCEEDED`、`PARTIAL` 或 `FAILED`。
9. 通知成员 5 生成受影响组合快照。
10. 在 `finally` 中调用 `RELEASE_LOCK()`。

## 7. MySQL 交付

- `market_data_sync_run`
- `market_price`
- 最近价格索引和同步运行索引
- 行情唯一键
- `latest_market_price` 视图

约束：

- 价格为正，成交量非负。
- 成功数 + 失败数不得超过请求数。
- `price_date` 与 `fetched_at` 分开保存。
- 重复同步不能创建重复行情。
- 本次失败不能删除旧行情。

## 8. 状态规则

| 状态 | 含义 |
|---|---|
| `FRESH` | 符合交易日历允许的最近交易日 |
| `STALE` | 有价格，但落后于最近应有交易日 |
| `UNAVAILABLE` | 从未获得有效价格 |

周末和休市日不能只按自然日判断陈旧。

## 9. 配置

| 变量 | 说明 |
|---|---|
| `MARKET_DATA_PROVIDER` | twelve-data 或 fixture |
| `MARKET_SYNC_CRON` | 调度表达式 |
| `MARKET_TIMEZONE` | 市场时区 |
| `MARKET_BATCH_SIZE` | 批量大小 |
| `MARKET_REQUEST_TIMEOUT_SECONDS` | 请求超时 |
| `MARKET_MAX_RETRIES` | 最大重试 |
| `TWELVE_DATA_API_KEY` | 条件必需 |

<!-- VERIFY: 实际部署的 provider 凭据、市场时区和调度表达式尚未确定 -->

## 10. 后端测试

### Provider/worker 测试

- 正常、空响应、无效代码、超时、限流和格式错误。
- 批处理、有限重试和部分成功。
- 周末、休市日和夏令时。
- 命名锁正常、冲突与异常释放。

### API 测试

- 手动同步的 202 响应。
- 已有任务时返回当前运行。
- 最近同步成功、部分成功和失败响应。
- 最新价格正常、陈旧、无数据和 404。

### 数据库测试

- 行情 upsert。
- 最近价格视图。
- 计数 CHECK 约束。
- 部分失败仍保留成功数据。

### OpenAPI 测试

- 同步请求、运行状态和价格 examples 可见。
- 202、200、404、409、503响应已配置。

## 11. 验收标准

### AC-MD-01：真实同步

股票和 ETF 价格成功写入 MySQL，并能通过最新价格接口查询。

### AC-MD-02：幂等

重复同步同一交易日不产生重复行。

### AC-MD-03：降级

provider 失败时旧价格仍可读取，并标为 `STALE`。

### AC-MD-04：接口样例

Swagger 中可看到同步请求和所有状态的响应 examples。

## 12. 交接

- 从成员 3 获取活跃标的。
- 向成员 1 提供同步和价格接口 examples。
- 向成员 5 提供最新价格视图和同步完成事件。

## 13. 完成定义

- 真实和 fixture provider 都可运行。
- 三个接口、两张表、一个视图和测试通过。
- 锁、重试、幂等和降级有自动测试。
