# PRD 3: Instruments, Trading, and Positions Backend

## Goal

Provide instrument discovery, an immutable trade ledger, and an accurate current
position projection.

## Scope

- Search active stock and ETF instruments.
- Record purchase and sale transactions.
- List transaction history and current positions.
- Require and enforce idempotency keys for trade creation.
- Reject sales that exceed the current position.

## Transaction rules

Trade creation validates portfolio ownership and instrument status, locks the
position row, calculates the new quantity, average cost, and realized P&L, writes
the immutable ledger entry, and updates or removes the projection in one database
transaction.

## Data delivery

The module owns `instruments`, `trade_transactions`, and `portfolio_positions`,
including constraints, indexes, seed instruments, and row-locking queries.

## Tests

Tests cover purchase and sale math, fees, full liquidation, overselling,
idempotency replay, concurrency, rollback, pagination, and OpenAPI examples.

## Acceptance criteria

- A purchase atomically creates a ledger entry and position.
- Reusing an idempotency key does not duplicate financial effects.
- An excessive sale returns a business conflict without changing data.
- Current positions contain only positive quantities.
