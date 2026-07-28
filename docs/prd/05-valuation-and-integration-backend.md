<!-- generated-by: gsd-doc-writer -->

# PRD 5: Portfolio Valuation, Analytics, and Backend Integration

## 1. Ownership

| Item | Details |
|---|---|
| Primary owner | Member 5 (replace with a name before submission) |
| Scope | Valuation APIs, analytics views/snapshots, backend integration tests, and CI |
| Primary data objects | `portfolio_valuation_snapshot` and analytics views |
| Out of scope | Frontend pages, transaction writes, and external market-data retrieval |
| Interface contracts | [API Reference](../API.md) / [OpenAPI Specification](../openapi.yaml) |
| Status | Pending implementation |

## 2. Module Goal

Transform current positions and latest market data into market value, cost, profit/loss, return, asset allocation, and historical trend data. Integrate health checks, complete MySQL validation, contract tests, and CI for all four backend modules.

## 3. Metric Definitions

- `marketValue = quantity × latestClosePrice`
- `costBasis = quantity × averageCost`
- `unrealizedPnl = marketValue - costBasis`
- `returnPct = unrealizedPnl ÷ costBasis × 100`
- `allocationPct = positionMarketValue ÷ portfolioPricedMarketValue × 100`

Rules:

- Use `BigDecimal` in Java and `DECIMAL` in MySQL.
- Positions without prices are excluded from priced market value and the allocation denominator.
- Return both total cost and priced cost.
- Return `null` when a denominator is zero.
- Do not round intermediate values prematurely to two decimal places.

## 4. Interfaces

| Method | Path | Success status |
|---|---|---:|
| GET | `/api/v1/portfolios/{portfolioId}/dashboard` | 200 |
| GET | `/api/v1/portfolios/{portfolioId}/performance` | 200 |
| GET | `/health/live` | 200 |
| GET | `/health/ready` | 200/503 |

See [API.md](../API.md) for complete query parameters, response fields, and examples.

## 5. Dashboard Response

The response must provide, in one request:

- Basic portfolio information.
- Total market value, total cost, priced cost, unrealized profit/loss, and return.
- Counts of priced and unpriced positions.
- Price, date, source, status, and metrics for each position.
- Asset-allocation array.
- Latest price date used.

The frontend should not have to compose multiple financial-calculation interfaces to render a single page.

## 6. Performance Response

- Support optional `from` and `to` dates.
- Sort by valuation date in ascending order.
- Each point includes market value, cost, profit/loss, and position counts.
- Do not invent data for dates without snapshots.

## 7. MySQL Deliverables

- `portfolio_valuation_snapshot`
- `position_metrics`
- `portfolio_allocation`
- `portfolio_summary`
- Portfolio/date unique key and historical-query indexes

Data-type requirements:

- Summary amounts use `DECIMAL(24,8)`.
- Percentages retain high precision.
- Timestamps use UTC.

## 8. Snapshot Flow

1. Receive the synchronization-completion event from Member 4.
2. Identify affected portfolios.
3. Read a consistent view of current positions and latest prices.
4. Create or update the daily snapshot.
5. Record the count of unpriced positions.
6. Record failures without deleting market data.

For historical rebuilds, replay transactions by trading day so each snapshot reflects the positions that actually existed on that date, rather than applying today's positions to every historical price.

## 9. Backend and Database Tests

### Calculation Tests

- Normal and negative profit/loss, zero cost, fractional shares, and eight-decimal prices.
- Single asset, multiple assets, and all positions without market data.
- Allocation percentages sum approximately to 100%.

### API Tests

- Complete dashboard response.
- Performance date filtering and sorting.
- User ownership and 404 behavior.
- Ready check returns 503 when MySQL fails.

### Database Tests

- Three analytics views against fixed fixture results.
- One snapshot per date.
- Positions without market data are counted but excluded from the denominator.
- Full MySQL 8 schema and migrations execute against an empty database.

### Contract and CI Tests

- Validate all actual backend responses against OpenAPI.
- CI runs unit and integration tests for all four modules.
- CI runs MySQL migrations, documentation-link checks, and secret scanning.
- Release candidates run the complete backend smoke test.

## 10. OpenAPI Integration Responsibility

- Verify that routes from all four backend owners appear in `/v3/api-docs`.
- Verify that every success and error response has a schema and examples.
- Compare the runtime schema with [openapi.yaml](../openapi.yaml).
- Treat contract differences as CI failures.

## 11. Acceptance Criteria

### AC-VA-01: Metric Consistency

For fixed positions and prices, MySQL views, the API, and documentation examples use the same formulas.

### AC-VA-02: Missing Market Data

A position without a price is counted separately and excluded from portfolio market value and the allocation denominator.

### AC-VA-03: Historical Trend

Performance points are ordered by date ascending, and missing dates are not backfilled with invented points.

### AC-VA-04: Complete Backend

An empty MySQL 8 instance can create eight tables, four views, and two triggers, and all backend tests pass.

### AC-VA-05: API Documentation

The runtime OpenAPI schema has no unapproved differences from the repository specification.

## 12. Handoffs

- Receive portfolio ownership from Member 2.
- Receive positions and cost from Member 3.
- Receive latest prices and synchronization events from Member 4.
- Provide dashboard, performance, and health examples to Member 1.

## 13. Definition of Done

- Four interfaces and all analytics data objects are complete.
- Precision, missing-market-data, snapshot, and contract tests pass.
- CI can start from an empty MySQL instance and validate the entire backend.
