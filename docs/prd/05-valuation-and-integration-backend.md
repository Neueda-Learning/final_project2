# PRD 5: Valuation, Analytics, and Backend Integration

## Goal

Integrate backend modules and provide authoritative portfolio metrics for the
dashboard and performance history.

## Metrics

- Priced market value is quantity multiplied by the latest valid close.
- Remaining cost basis follows the trading projection.
- Unrealized P&L is market value minus cost basis.
- Return percentage is unrealized P&L divided by cost basis.
- Allocation weight uses total priced market value as the denominator.
- Holdings without a valid price are excluded from priced totals and reported
  separately.

## API scope

The dashboard response contains a summary, valued positions, allocation, price
status, and valuation date. The performance response contains ordered daily
snapshot points for an optional date range.

## Snapshot flow

After market-data synchronization, analytics calculates a consistent portfolio
valuation and upserts one snapshot per portfolio and valuation date.

## Tests

Tests cover all formulas, zero-cost and missing-price cases, mixed instruments,
historical ordering, snapshot idempotency, module integration, health checks, and
runtime OpenAPI compatibility.

## Acceptance criteria

- Summary, positions, and allocation reconcile to the same source data.
- Missing market data remains visible without becoming a zero price.
- Historical snapshots are stable and correctly ordered.
- The complete backend test suite and contract checks pass.
