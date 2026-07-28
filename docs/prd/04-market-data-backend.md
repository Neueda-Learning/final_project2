# PRD 4: Market Data Backend

## Goal

Synchronize real daily close prices while keeping provider failures isolated from
portfolio and trading operations.

## Scope

- Trigger synchronization manually or through the scheduled worker.
- Track requested, successful, and failed instrument counts.
- Return the latest synchronization run and latest instrument price.
- Support a live provider and a deterministic fixture provider for tests.

## Price conventions

Prices are positive decimal values keyed by instrument, provider, and trading date.
Repeated synchronization performs an idempotent upsert. Fresh, stale, and
unavailable states are derived explicitly and never represented by a fabricated
zero price.

## Configuration

Provider name, API key, base URL, timeouts, retry limits, schedule, and freshness
threshold are supplied through environment-backed configuration. Secrets must
never appear in source, logs, or API responses.

## Tests

Tests cover provider mapping, rate limits, retries, partial failure, idempotent
upserts, synchronization status, fixture behavior, and runtime contract examples.

## Acceptance criteria

- The live provider can persist a real daily close with a configured key.
- Repeated runs do not create duplicate prices.
- Provider failure produces a useful partial or failed status.
- Existing portfolio and trade operations remain available during provider failure.
