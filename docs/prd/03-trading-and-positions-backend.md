<!-- generated-by: gsd-doc-writer -->

# PRD 3: Instruments, Trading, and Positions Backend

## 1. Ownership

| Item | Details |
|---|---|
| Primary owner | Member 3 (replace with a name before submission) |
| Scope | Instrument lookup, transaction/position APIs, MySQL, and backend tests |
| Primary tables | `instrument`, `trade_transaction`, `portfolio_position` |
| Out of scope | Frontend, market-data synchronization, and portfolio valuation |
| Interface contracts | [API Reference](../API.md) / [OpenAPI Specification](../openapi.yaml) |
| Status | Pending implementation |

## 2. Module Goal

Provide stock/ETF search, selectable stored daily closes, buy and sell operations, transaction history, and current-position interfaces. Guarantee that clients cannot invent execution prices, as well as idempotency, financial precision, oversell validation, and atomic updates of transactions and the position projection.

## 3. Interfaces

| Method | Path | Success status |
|---|---|---:|
| GET | `/api/v1/instruments?query={text}` | 200 |
| GET | `/api/v1/instruments/{instrumentId}/tradable-prices?limit=60` | 200 |
| POST | `/api/v1/portfolios/{portfolioId}/transactions` | 201 |
| GET | `/api/v1/portfolios/{portfolioId}/transactions` | 200 |
| GET | `/api/v1/portfolios/{portfolioId}/positions` | 200 |

See [API.md](../API.md) for request fields and response examples. Member 3 must configure the same examples in Spring MVC controllers and Springdoc annotations.

## 4. Trading Rules

- Instruments are restricted to `STOCK` and `ETF`.
- Quantity and execution price must be greater than zero; fees cannot be negative.
- A transaction request supplies `priceDate`, not `unitPrice`, `currency`, or `executedAt`.
- `priceDate` must match a stored real daily close returned by `tradable-prices`.
- The server uses that close as `unitPrice`, derives instrument currency, and records a deterministic 16:00 market-close timestamp.
- The MVP is long-only.
- Sell quantity cannot exceed the current position.
- Idempotency keys are unique within a portfolio.
- Use `BigDecimal` in Java and `DECIMAL` in MySQL.
- Transactions cannot be updated or deleted normally; corrections use compensating transactions.

## 5. Transaction Flow

1. Validate portfolio ownership and instrument status.
2. Resolve `priceDate` to a stored daily close; reject a missing date or price.
3. Derive `unitPrice`, currency, and the deterministic market-close execution timestamp.
4. Check the portfolio and idempotency key.
5. Lock the position with `SELECT ... FOR UPDATE`.
6. Validate SELL quantity.
7. Calculate the new quantity, average cost, and realized profit/loss.
8. Insert an immutable transaction.
9. Insert or update the position projection and version.
10. Commit everything in one MySQL transaction or roll everything back.

## 6. MySQL Deliverables

- `instrument`
- `trade_transaction`
- `portfolio_position`
- Idempotency unique key, composite primary key, foreign keys, and query indexes
- Two `SIGNAL` triggers that prevent transaction UPDATE and DELETE

Data types:

| Data | Type |
|---|---|
| UUID | `CHAR(36)` |
| Quantity | `DECIMAL(28,8)` |
| Price/cost/fee | `DECIMAL(20,8)` |
| Time | `DATETIME(6)` UTC |

## 7. Standard Errors

| Error code | HTTP status |
|---|---:|
| `INSTRUMENT_NOT_FOUND` | 404 |
| `INSTRUMENT_INACTIVE` | 409 |
| `POSITION_INSUFFICIENT_QUANTITY` | 409 |
| `IDEMPOTENCY_CONFLICT` | 409 |
| `CONCURRENT_POSITION_UPDATE` | 409 |
| `VALIDATION_ERROR` | 422 |

## 8. Backend Tests

### Domain Tests

- Initial buy, additional buy, partial sell, and full sell.
- Stored-price resolution, missing date/price rejection, and server-derived execution fields.
- Weighted-average cost, fees, and realized profit/loss.
- Fractional shares and eight-decimal precision boundaries.
- Overselling and zero/negative inputs.

### API Tests

- Successful requests and error responses for all five interfaces.
- Pagination, sorting, and stable ordering.
- Idempotent replay and conflict when the same key has a different request.
- Portfolio ownership.

### Database and Concurrency Tests

- Reject negative quantity, zero price, and duplicate idempotency key.
- Triggers reject transaction updates and deletes.
- Concurrent buys do not lose updates.
- A failed transaction rolls back the position update.

### OpenAPI Tests

- Every interface includes request parameters, success responses, and error examples.
- Actual responses validate against the OpenAPI schema.

## 9. Acceptance Criteria

### AC-TR-01: Atomic Buy

After a buy is created from a valid selectable `priceDate`, its stored close is recorded as unit price and both the transaction and position exist; if any operation fails, neither remains.

### AC-TR-02: Idempotency

Submit the same request and idempotency key twice: the database contains one transaction and the position increases once.

### AC-TR-03: Oversell

Selling more than the position returns 409 `POSITION_INSUFFICIENT_QUANTITY` and leaves data unchanged.

### AC-TR-04: Interface Examples

Swagger UI displays a complete JSON body for transaction requests and shows 201, 409, and 422 response examples.

## 10. Handoffs

- Depend on Member 2's portfolio-ownership validation.
- Provide instrument, transaction, and position interface examples to Member 1.
- Provide the active-instrument set to Member 4.
- Provide position quantity and average cost to Member 5.

## 11. Definition of Done

- Five interfaces, three tables/migrations, and all tests pass.
- Database integration evidence covers transactions, idempotency, and concurrency.
- API.md, OpenAPI, and actual responses agree.
