<!-- generated-by: gsd-doc-writer -->

# Portfolio Manager Architecture

## Purpose and scope

Portfolio Manager is an MVP for creating portfolios, recording stock and ETF
trades, synchronizing daily prices, and reviewing valuation, profit and loss,
allocation, and historical performance. It does not provide brokerage execution,
real-time streaming quotes, tax accounting, or multi-currency conversion.

## Technology baseline

| Layer | Technology |
|---|---|
| Web client | React, TypeScript, Vite, TanStack Query, Chart.js |
| API | Java 21, Spring Boot, Spring MVC, Spring Data JPA |
| Worker | Spring Boot scheduled process |
| Database | MySQL 8 |
| Contract | OpenAPI 3.1 |
| Runtime | Docker Compose |

## Components

```text
Browser
  -> React client
      -> Spring MVC API
          -> Portfolio, trading, market-data, and analytics services
              -> MySQL
      -> Market-data worker
          -> External provider
          -> MySQL
```

The client owns presentation, navigation, localization, and request state. The API
owns validation, authorization boundaries, business rules, transactions, and
financial calculations. The worker owns provider communication, retries, daily
price upserts, and synchronization status.

## Core data model

- `users` provides the ownership boundary.
- `portfolios` stores portfolio metadata and base currency.
- `instruments` stores supported stock and ETF metadata.
- `trade_transactions` is an immutable trading ledger.
- `portfolio_positions` is the current-position projection.
- `daily_prices` stores provider-specific daily close prices.
- `market_data_sync_runs` records synchronization outcomes.
- `portfolio_valuation_snapshots` stores daily analytics history.

## Critical flows

1. Creating a trade validates portfolio ownership and instrument status.
2. The service locks the current position, applies buy or sell rules, writes the
   immutable transaction, and updates the position in one database transaction.
3. The worker fetches daily prices and performs idempotent upserts.
4. Analytics combines positions with the latest valid prices and records snapshots.
5. The dashboard reads a single consistent analytics response for summary,
   positions, and allocation.

## API and data conventions

- Business endpoints use the `/api/v1` prefix.
- Financial values are serialized as decimal strings.
- Timestamps use ISO 8601 UTC; trading dates use `YYYY-MM-DD`.
- Errors use a stable `code`, a safe `message`, optional `fieldErrors`, and a
  `requestId`.
- Transaction creation requires an `Idempotency-Key`.
- Resources outside the current user boundary return 404.

## Financial definitions

- Cost basis includes purchase fees.
- Realized P&L is recognized on sales.
- Unrealized P&L is priced market value minus remaining cost basis.
- Return percentage is unrealized P&L divided by remaining cost basis.
- Missing prices are never treated as zero and are excluded from priced totals.

## Reliability and security

Database constraints enforce uniqueness and referential integrity. Position updates
use row locks, and market-data writes are idempotent. Secrets are supplied through
environment variables and must never appear in logs or API responses. Health
endpoints distinguish process liveness from dependency readiness.

## Definition of done

The MVP is complete when the full create-portfolio, record-trade, synchronize-price,
and review-dashboard flow works against MySQL; automated tests pass; OpenAPI matches
runtime behavior; and degraded market-data states remain understandable.
