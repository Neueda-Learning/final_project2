<!-- generated-by: gsd-doc-writer -->

# Portfolio Manager REST API 接口说明

## 1. 文档状态

本文定义 REST API 契约。仓库已包含 Spring Boot 骨架；接口路径、请求和响应样例是后续业务实现与契约测试的开发基线。

- OpenAPI 规范：[openapi.yaml](openapi.yaml)
- Swagger UI：`GET /docs`
- OpenAPI JSON：`GET /v3/api-docs`
- 本地基础地址：`http://localhost:8000`
- 业务 API 前缀：`/api/v1`

## 2. 接口负责人

| Tag | 负责人 | 范围 |
|---|---|---|
| Portfolios | 成员 2 | 用户上下文、组合 CRUD/归档 |
| Trading | 成员 3 | 标的搜索、交易、持仓 |
| Market Data | 成员 4 | 行情同步、同步状态、最新价格 |
| Analytics | 成员 5 | Dashboard、Performance |
| Health | 成员 5 | 存活和就绪检查 |

成员 1 仅消费接口并实现全部前端。

## 3. Swagger/OpenAPI 配置

Springdoc 从 Spring MVC Controller、Java records 和 Bean Validation 注解生成
OpenAPI。项目使用以下配置基线：

```xml
<dependency>
  <groupId>org.springdoc</groupId>
  <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
  <version>3.0.3</version>
</dependency>
```

```yaml
springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /docs
```

生产环境可以通过 Spring Security 限制文档入口，但测试与演示环境必须开放接口文档。

路由实现要求：

- 使用 Java records 作为请求/响应 DTO，JPA 实体不得直接暴露。
- 使用 `@Valid` 与 Bean Validation 声明字段约束。
- 使用 `@Operation`、`@ApiResponse`、`@Schema` 和 `@ExampleObject` 补充语义与 examples。
- 使用 `@RestControllerAdvice` 输出统一错误体。
- 成功和错误响应都必须出现在 `/v3/api-docs`。

运行时文档由 Springdoc 生成，仓库中的 [openapi.yaml](openapi.yaml) 是评审和前端类型生成使用的权威契约。

## 4. 通用协议

### 4.1 请求头

| Header | 必需 | 说明 |
|---|---|---|
| `Content-Type: application/json` | 有 JSON body 时 | 请求体类型 |
| `Accept: application/json` | 建议 | 响应类型 |
| `X-Request-ID` | 可选 | 客户端追踪 ID；缺失时服务端生成 |
| `Idempotency-Key` | 创建交易时必需 | 网络重试复用同一值 |

MVP 使用服务端预置演示用户，不接受客户端通过 header/body 任意指定用户 ID。

### 4.2 数据格式

| 数据 | JSON 格式 | 样例 |
|---|---|---|
| UUID | string | `"22222222-2222-2222-2222-222222222222"` |
| 金额/价格/数量/百分比 | decimal string | `"1250.00000000"` |
| 时间 | ISO 8601 UTC | `"2026-07-27T08:00:00Z"` |
| 交易日 | `YYYY-MM-DD` | `"2026-07-24"` |
| 币种 | 3 位大写字符串 | `"USD"` |
| 股票/ETF 类型 | enum | `"STOCK"` / `"ETF"` |

所有金融数值以字符串传输，避免 JSON 二进制浮点精度损失。

### 4.3 分页响应

```json
{
  "items": [],
  "page": 1,
  "pageSize": 20,
  "total": 0
}
```

### 4.4 标准错误响应

```json
{
  "code": "POSITION_INSUFFICIENT_QUANTITY",
  "message": "卖出数量超过当前持仓。",
  "fieldErrors": {
    "quantity": [
      "Must not exceed the current position."
    ]
  },
  "requestId": "req-01J3X6F8NQ8H2A3V"
}
```

常见状态：

| HTTP | 含义 |
|---:|---|
| 400 | 请求语义错误 |
| 404 | 资源不存在或不属于当前用户 |
| 409 | 唯一键、幂等、并发或业务状态冲突 |
| 422 | 字段格式/范围验证失败 |
| 500 | 未预期服务错误 |
| 503 | 数据库或依赖未就绪 |

## 5. Portfolios

### 5.1 查询组合列表

`GET /api/v1/portfolios`

请求：

```bash
curl -s \
  "http://localhost:8000/api/v1/portfolios?page=1&pageSize=20" \
  -H "Accept: application/json" \
  -H "X-Request-ID: demo-list-portfolios"
```

查询参数：

| 参数 | 类型 | 默认 | 说明 |
|---|---|---:|---|
| `page` | integer | 1 | 页码，最小 1 |
| `pageSize` | integer | 20 | 每页 1–100 |
| `includeArchived` | boolean | false | 是否包含已归档组合 |

200 响应：

```json
{
  "items": [
    {
      "id": "22222222-2222-2222-2222-222222222222",
      "name": "Long-term Growth",
      "description": "US stocks and ETFs",
      "baseCurrency": "USD",
      "isArchived": false,
      "createdAt": "2026-07-27T08:00:00Z",
      "updatedAt": "2026-07-27T08:00:00Z"
    }
  ],
  "page": 1,
  "pageSize": 20,
  "total": 1
}
```

错误：422 `VALIDATION_ERROR`。

### 5.2 创建组合

`POST /api/v1/portfolios`

请求：

```bash
curl -s -X POST \
  "http://localhost:8000/api/v1/portfolios" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Long-term Growth",
    "description": "US stocks and ETFs",
    "baseCurrency": "USD"
  }'
```

请求体：

```json
{
  "name": "Long-term Growth",
  "description": "US stocks and ETFs",
  "baseCurrency": "USD"
}
```

201 响应：

```json
{
  "id": "22222222-2222-2222-2222-222222222222",
  "name": "Long-term Growth",
  "description": "US stocks and ETFs",
  "baseCurrency": "USD",
  "isArchived": false,
  "createdAt": "2026-07-27T08:00:00Z",
  "updatedAt": "2026-07-27T08:00:00Z"
}
```

错误：

- 409 `PORTFOLIO_NAME_CONFLICT`
- 422 `VALIDATION_ERROR`

### 5.3 查询组合详情

`GET /api/v1/portfolios/{portfolioId}`

请求：

```bash
curl -s \
  "http://localhost:8000/api/v1/portfolios/22222222-2222-2222-2222-222222222222"
```

200 响应：

```json
{
  "id": "22222222-2222-2222-2222-222222222222",
  "name": "Long-term Growth",
  "description": "US stocks and ETFs",
  "baseCurrency": "USD",
  "isArchived": false,
  "createdAt": "2026-07-27T08:00:00Z",
  "updatedAt": "2026-07-27T08:00:00Z"
}
```

错误：404 `PORTFOLIO_NOT_FOUND`。

### 5.4 修改组合

`PATCH /api/v1/portfolios/{portfolioId}`

请求：

```bash
curl -s -X PATCH \
  "http://localhost:8000/api/v1/portfolios/22222222-2222-2222-2222-222222222222" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Core Growth",
    "description": "Updated description"
  }'
```

请求体：

```json
{
  "name": "Core Growth",
  "description": "Updated description"
}
```

200 响应：

```json
{
  "id": "22222222-2222-2222-2222-222222222222",
  "name": "Core Growth",
  "description": "Updated description",
  "baseCurrency": "USD",
  "isArchived": false,
  "createdAt": "2026-07-27T08:00:00Z",
  "updatedAt": "2026-07-27T09:00:00Z"
}
```

错误：

- 404 `PORTFOLIO_NOT_FOUND`
- 409 `PORTFOLIO_NAME_CONFLICT`
- 422 `VALIDATION_ERROR`

### 5.5 删除空组合

`DELETE /api/v1/portfolios/{portfolioId}`

请求：

```bash
curl -i -X DELETE \
  "http://localhost:8000/api/v1/portfolios/22222222-2222-2222-2222-222222222222"
```

成功响应：`204 No Content`，无响应体。

错误：

- 404 `PORTFOLIO_NOT_FOUND`
- 409 `PORTFOLIO_HAS_HISTORY`

### 5.6 归档组合

`POST /api/v1/portfolios/{portfolioId}/archive`

请求：

```bash
curl -s -X POST \
  "http://localhost:8000/api/v1/portfolios/22222222-2222-2222-2222-222222222222/archive"
```

200 响应：

```json
{
  "id": "22222222-2222-2222-2222-222222222222",
  "name": "Core Growth",
  "description": "Updated description",
  "baseCurrency": "USD",
  "isArchived": true,
  "createdAt": "2026-07-27T08:00:00Z",
  "updatedAt": "2026-07-27T10:00:00Z"
}
```

错误：404 `PORTFOLIO_NOT_FOUND`。

## 6. Trading

### 6.1 搜索股票与 ETF

`GET /api/v1/instruments`

请求：

```bash
curl -s \
  "http://localhost:8000/api/v1/instruments?query=AAPL&limit=10"
```

查询参数：

| 参数 | 类型 | 必需 | 说明 |
|---|---|---|---|
| `query` | string | 是 | 代码或名称，1–100 字符 |
| `limit` | integer | 否 | 默认 10，最大 50 |

200 响应：

```json
{
  "items": [
    {
      "id": "33333333-3333-3333-3333-333333333333",
      "symbol": "AAPL",
      "name": "Apple Inc.",
      "assetType": "STOCK",
      "exchangeCode": "NASDAQ",
      "currency": "USD",
      "isActive": true
    }
  ]
}
```

错误：422 `VALIDATION_ERROR`。

### 6.2 创建买入/卖出交易

`POST /api/v1/portfolios/{portfolioId}/transactions`

请求：

```bash
curl -s -X POST \
  "http://localhost:8000/api/v1/portfolios/22222222-2222-2222-2222-222222222222/transactions" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-buy-aapl-001" \
  -d '{
    "instrumentId": "33333333-3333-3333-3333-333333333333",
    "side": "BUY",
    "quantity": "10.00000000",
    "unitPrice": "198.42000000",
    "feeAmount": "1.25000000",
    "executedAt": "2026-07-27T08:30:00Z",
    "note": "Initial position"
  }'
```

请求 header：

```text
Idempotency-Key: demo-buy-aapl-001
```

请求体：

```json
{
  "instrumentId": "33333333-3333-3333-3333-333333333333",
  "side": "BUY",
  "quantity": "10.00000000",
  "unitPrice": "198.42000000",
  "feeAmount": "1.25000000",
  "executedAt": "2026-07-27T08:30:00Z",
  "note": "Initial position"
}
```

201 响应：

```json
{
  "id": "55555555-5555-5555-5555-555555555555",
  "portfolioId": "22222222-2222-2222-2222-222222222222",
  "instrumentId": "33333333-3333-3333-3333-333333333333",
  "symbol": "AAPL",
  "side": "BUY",
  "quantity": "10.00000000",
  "unitPrice": "198.42000000",
  "feeAmount": "1.25000000",
  "currency": "USD",
  "executedAt": "2026-07-27T08:30:00Z",
  "note": "Initial position",
  "createdAt": "2026-07-27T08:30:01Z"
}
```

错误：

- 404 `PORTFOLIO_NOT_FOUND`
- 404 `INSTRUMENT_NOT_FOUND`
- 409 `INSTRUMENT_INACTIVE`
- 409 `POSITION_INSUFFICIENT_QUANTITY`
- 409 `IDEMPOTENCY_CONFLICT`
- 409 `CONCURRENT_POSITION_UPDATE`
- 422 `VALIDATION_ERROR`

### 6.3 查询交易历史

`GET /api/v1/portfolios/{portfolioId}/transactions`

请求：

```bash
curl -s \
  "http://localhost:8000/api/v1/portfolios/22222222-2222-2222-2222-222222222222/transactions?page=1&pageSize=20&sort=-executedAt"
```

200 响应：

```json
{
  "items": [
    {
      "id": "55555555-5555-5555-5555-555555555555",
      "portfolioId": "22222222-2222-2222-2222-222222222222",
      "instrumentId": "33333333-3333-3333-3333-333333333333",
      "symbol": "AAPL",
      "side": "BUY",
      "quantity": "10.00000000",
      "unitPrice": "198.42000000",
      "feeAmount": "1.25000000",
      "currency": "USD",
      "executedAt": "2026-07-27T08:30:00Z",
      "note": "Initial position",
      "createdAt": "2026-07-27T08:30:01Z"
    }
  ],
  "page": 1,
  "pageSize": 20,
  "total": 1
}
```

错误：

- 404 `PORTFOLIO_NOT_FOUND`
- 422 `VALIDATION_ERROR`

### 6.4 查询当前持仓

`GET /api/v1/portfolios/{portfolioId}/positions`

请求：

```bash
curl -s \
  "http://localhost:8000/api/v1/portfolios/22222222-2222-2222-2222-222222222222/positions"
```

200 响应：

```json
{
  "items": [
    {
      "instrumentId": "33333333-3333-3333-3333-333333333333",
      "symbol": "AAPL",
      "name": "Apple Inc.",
      "assetType": "STOCK",
      "quantity": "10.00000000",
      "averageCost": "198.54500000",
      "realizedPnl": "0.00000000",
      "openedAt": "2026-07-27T08:30:01Z",
      "updatedAt": "2026-07-27T08:30:01Z"
    }
  ]
}
```

错误：404 `PORTFOLIO_NOT_FOUND`。

## 7. Market Data

### 7.1 手动触发行情同步

`POST /api/v1/market-data/sync`

请求：

```bash
curl -s -X POST \
  "http://localhost:8000/api/v1/market-data/sync" \
  -H "Content-Type: application/json" \
  -d '{
    "force": false
  }'
```

请求体：

```json
{
  "force": false
}
```

202 响应：

```json
{
  "id": "66666666-6666-6666-6666-666666666666",
  "provider": "twelve-data",
  "status": "RUNNING",
  "requestedCount": 2,
  "successCount": 0,
  "failureCount": 0,
  "startedAt": "2026-07-27T09:00:00Z",
  "completedAt": null,
  "triggeredBy": "MANUAL",
  "errorSummary": null
}
```

错误：

- 409 `MARKET_SYNC_ALREADY_RUNNING`
- 503 `MARKET_PROVIDER_UNAVAILABLE`

### 7.2 查询最近同步

`GET /api/v1/market-data/sync-runs/latest`

请求：

```bash
curl -s \
  "http://localhost:8000/api/v1/market-data/sync-runs/latest"
```

200 响应：

```json
{
  "id": "66666666-6666-6666-6666-666666666666",
  "provider": "twelve-data",
  "status": "SUCCEEDED",
  "requestedCount": 2,
  "successCount": 2,
  "failureCount": 0,
  "startedAt": "2026-07-27T09:00:00Z",
  "completedAt": "2026-07-27T09:00:08Z",
  "triggeredBy": "MANUAL",
  "errorSummary": null
}
```

若从未同步，200 响应：

```json
null
```

### 7.3 查询标的最新价格

`GET /api/v1/instruments/{instrumentId}/latest-price`

请求：

```bash
curl -s \
  "http://localhost:8000/api/v1/instruments/33333333-3333-3333-3333-333333333333/latest-price"
```

200 响应：

```json
{
  "instrumentId": "33333333-3333-3333-3333-333333333333",
  "symbol": "AAPL",
  "priceDate": "2026-07-24",
  "closePrice": "213.55000000",
  "adjustedClose": "213.55000000",
  "currency": "USD",
  "source": "twelve-data",
  "sourceTimestamp": null,
  "fetchedAt": "2026-07-27T09:00:05Z",
  "priceStatus": "FRESH"
}
```

错误：

- 404 `INSTRUMENT_NOT_FOUND`
- 404 `MARKET_PRICE_NOT_FOUND`

## 8. Analytics

### 8.1 查询 Dashboard

`GET /api/v1/portfolios/{portfolioId}/dashboard`

请求：

```bash
curl -s \
  "http://localhost:8000/api/v1/portfolios/22222222-2222-2222-2222-222222222222/dashboard"
```

200 响应：

```json
{
  "portfolio": {
    "id": "22222222-2222-2222-2222-222222222222",
    "name": "Long-term Growth",
    "baseCurrency": "USD"
  },
  "summary": {
    "positionCount": 2,
    "pricedPositionCount": 2,
    "unpricedPositionCount": 0,
    "pricedMarketValue": "125430.18000000",
    "totalCostBasis": "110000.00000030",
    "pricedCostBasis": "110000.00000030",
    "unrealizedPnl": "15430.17999970",
    "returnPct": "14.02743636",
    "newestPriceDate": "2026-07-24",
    "oldestUsedPriceDate": "2026-07-24"
  },
  "positions": [
    {
      "instrumentId": "33333333-3333-3333-3333-333333333333",
      "symbol": "AAPL",
      "name": "Apple Inc.",
      "assetType": "STOCK",
      "quantity": "400.00000000",
      "averageCost": "198.54500000",
      "costBasis": "79418.00000000",
      "closePrice": "213.55000000",
      "priceDate": "2026-07-24",
      "priceSource": "twelve-data",
      "priceStatus": "FRESH",
      "marketValue": "85420.00000000",
      "unrealizedPnl": "6002.00000000",
      "returnPct": "7.55748067",
      "allocationPct": "68.10163232"
    },
    {
      "instrumentId": "44444444-4444-4444-4444-444444444444",
      "symbol": "SPY",
      "name": "SPDR S&P 500 ETF Trust",
      "assetType": "ETF",
      "quantity": "70.00000000",
      "averageCost": "436.88571429",
      "costBasis": "30582.00000030",
      "closePrice": "571.57400000",
      "priceDate": "2026-07-24",
      "priceSource": "twelve-data",
      "priceStatus": "FRESH",
      "marketValue": "40010.18000000",
      "unrealizedPnl": "9428.17999970",
      "returnPct": "30.82918056",
      "allocationPct": "31.89836768"
    }
  ],
  "allocation": [
    {
      "instrumentId": "33333333-3333-3333-3333-333333333333",
      "symbol": "AAPL",
      "marketValue": "85420.00000000",
      "allocationPct": "68.10163232"
    },
    {
      "instrumentId": "44444444-4444-4444-4444-444444444444",
      "symbol": "SPY",
      "marketValue": "40010.18000000",
      "allocationPct": "31.89836768"
    }
  ]
}
```

错误：404 `PORTFOLIO_NOT_FOUND`。

### 8.2 查询估值历史

`GET /api/v1/portfolios/{portfolioId}/performance`

请求：

```bash
curl -s \
  "http://localhost:8000/api/v1/portfolios/22222222-2222-2222-2222-222222222222/performance?from=2026-07-01&to=2026-07-27"
```

查询参数：

| 参数 | 类型 | 必需 | 说明 |
|---|---|---|---|
| `from` | date | 否 | 起始估值日期 |
| `to` | date | 否 | 结束估值日期 |

200 响应：

```json
{
  "portfolioId": "22222222-2222-2222-2222-222222222222",
  "baseCurrency": "USD",
  "points": [
    {
      "valuationDate": "2026-07-23",
      "pricedMarketValue": "124810.00000000",
      "totalCostBasis": "110000.00000000",
      "pricedCostBasis": "110000.00000000",
      "unrealizedPnl": "14810.00000000",
      "pricedPositionCount": 2,
      "unpricedPositionCount": 0
    },
    {
      "valuationDate": "2026-07-24",
      "pricedMarketValue": "125430.18000000",
      "totalCostBasis": "110000.00000000",
      "pricedCostBasis": "110000.00000000",
      "unrealizedPnl": "15430.18000000",
      "pricedPositionCount": 2,
      "unpricedPositionCount": 0
    }
  ]
}
```

错误：

- 404 `PORTFOLIO_NOT_FOUND`
- 422 `INVALID_DATE_RANGE`

## 9. Health

### 9.1 存活检查

`GET /health/live`

请求：

```bash
curl -s "http://localhost:8000/health/live"
```

200 响应：

```json
{
  "status": "ok"
}
```

### 9.2 就绪检查

`GET /health/ready`

请求：

```bash
curl -s "http://localhost:8000/health/ready"
```

200 响应：

```json
{
  "status": "ready",
  "checks": {
    "mysql": "ok"
  }
}
```

503 响应：

```json
{
  "code": "SERVICE_NOT_READY",
  "message": "MySQL dependency is unavailable.",
  "fieldErrors": {},
  "requestId": "req-01J3X6F8NQ8H2A3V"
}
```

## 10. OpenAPI 完成标准

- [openapi.yaml](openapi.yaml) 可被 YAML 解析器读取。
- 每个 endpoint 都有唯一 `operationId`。
- 每个请求体和成功响应都有 schema 与 example。
- 每个业务接口至少配置一个标准错误响应。
- 204 响应不声明 JSON body。
- 所有 `$ref` 均能解析到 `components/schemas`。
- 运行时 `/v3/api-docs` 与仓库规范无未批准差异。
- 前端类型由规范生成或在 CI 中进行兼容性校验。
