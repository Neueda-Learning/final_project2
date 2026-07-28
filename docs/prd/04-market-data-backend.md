<!-- generated-by: gsd-doc-writer -->

# PRD 4: Market-Data Synchronization Backend

## 1. Ownership

| Item | Details |
|---|---|
| Primary owner | Member 4 (replace with a name before submission) |
| Scope | Market-data provider, worker, synchronization API, MySQL, and backend tests |
| Primary tables | `market_data_sync_run`, `market_price` |
| Out of scope | Frontend and valuation charts |
| Interface contracts | [API Reference](../API.md) / [OpenAPI Specification](../openapi.yaml) |
| Status | Implemented (real environments require `TWELVE_DATA_API_KEY`) |

## 2. Module Goal

Fetch the latest daily closing prices for stocks and ETFs from a real external provider, persist them idempotently in MySQL, and provide price date, source, freshness, and synchronization-run status to the frontend and valuation module.

## 3. Market-Data Definition

- The MVP uses the latest available daily closing price, not streaming intraday quotes.
- The first real provider is the Twelve Data REST API.
- Isolate providers behind the Java `MarketDataProvider` interface.
- Use a fixture provider for automated tests and offline demonstrations.
- Preserve the last successful price when an external request fails.

## 4. Interfaces

| Method | Path | Success status |
|---|---|---:|
| POST | `/api/v1/market-data/sync` | 202 |
| GET | `/api/v1/market-data/sync-runs/latest` | 200 |
| GET | `/api/v1/instruments/{instrumentId}/latest-price` | 200 |
| GET | `/api/v1/instruments/{instrumentId}/tradable-prices?limit=60` | 200 |

See [API.md](../API.md) and [openapi.yaml](../openapi.yaml) for request and response examples.

## 5. Provider Interface

```text
search_instruments(query, limit)
fetch_daily_closes(symbols, start_date, end_date)
health_check()
```

A normalized price contains:

- Instrument ID and provider symbol
- Price date
- Open, high, low, close, and adjusted close
- Volume, currency, and source
- Source timestamp and fetch time

## 6. Synchronization Flow

1. Acquire a global named lock using MySQL `GET_LOCK()`.
2. Create a `RUNNING` synchronization record.
3. Query active instruments whose position quantity is greater than zero.
4. Call the provider in batches with timeouts and limited retries.
5. Validate symbol, date, currency, and positive price.
6. Upsert by instrument + trading date + source.
7. Record successful, failed, and categorized errors.
8. Mark the run `SUCCEEDED`, `PARTIAL`, or `FAILED`.
9. Notify Member 5 to generate snapshots for affected portfolios.
10. Call `RELEASE_LOCK()` in `finally`.

The current Twelve Data implementation requests one instrument at a time (`MARKET_BATCH_SIZE=1`) because multi-symbol historical responses may truncate the requested time range. Synchronization requests approximately one month of history and replays historical transactions to rebuild daily valuation snapshots.

## 7. MySQL Deliverables

- `market_data_sync_run`
- `market_price`
- Latest-price and synchronization-run indexes
- Market-price unique key
- `latest_market_price` view

Constraints:

- Prices are positive and volume is non-negative.
- Successful count + failed count cannot exceed requested count.
- Store `price_date` separately from `fetched_at`.
- Repeated synchronization cannot create duplicate prices.
- A failed run cannot delete old prices.

## 8. Status Rules

| Status | Meaning |
|---|---|
| `FRESH` | Matches the latest trading date permitted by the market calendar |
| `STALE` | A price exists but predates the latest expected trading date |
| `UNAVAILABLE` | No valid price has ever been obtained |

Do not determine staleness from calendar-day age alone; account for weekends and market holidays.

## 9. Configuration

| Variable | Description |
|---|---|
| `MARKET_DATA_PROVIDER` | `twelve-data` or `fixture` |
| `MARKET_SYNC_CRON` | Schedule expression |
| `MARKET_TIMEZONE` | Market time zone |
| `MARKET_BATCH_SIZE` | Batch size; defaults to `1` for complete historical responses |
| `MARKET_REQUEST_TIMEOUT_SECONDS` | Request timeout |
| `MARKET_MAX_RETRIES` | Maximum retries |
| `TWELVE_DATA_API_KEY` | Conditionally required |

The defaults use Twelve Data, the `America/New_York` market time zone, and a weekday after-close schedule. Supply credentials and configuration overrides through environment variables in deployed environments.

## 10. Backend Tests

### Provider and Worker Tests

- Successful and empty responses, invalid symbols, timeout, rate limiting, and malformed data.
- Batching, limited retry, and partial success.
- Weekends, market holidays, and daylight-saving time.
- Named-lock acquisition, conflict, and release after exceptions.
- Historical transaction replay and daily valuation-snapshot reconstruction.

### API Tests

- 202 response for manual synchronization.
- Return the current run when a task already exists.
- Latest-run responses for success, partial success, and failure.
- Latest-price responses for fresh, stale, unavailable, and 404 cases.
- Tradable-price history returns only stored real daily closes in descending date order and enforces its limit.

### Database Tests

- Market-price upsert.
- Latest-price view.
- Count `CHECK` constraints.
- Partial failure preserves successful data.

### OpenAPI Tests

- Synchronization request, run status, and price examples are visible.
- 202, 200, 404, 409, and 503 responses are configured.

## 11. Acceptance Criteria

### AC-MD-01: Real Synchronization

Stock and ETF prices are written successfully to MySQL and can be queried through the latest-price interface.

### AC-MD-02: Idempotency

Repeated synchronization for the same trading day does not produce duplicate rows.

### AC-MD-03: Degradation

When the provider fails, old prices remain readable and are marked `STALE`.

### AC-MD-04: Interface Examples

Swagger displays the synchronization request and response examples for every status.

## 12. Handoffs

- Receive active instruments from Member 3.
- Provide synchronization and price interface examples to Member 1.
- Provide the latest-price view and synchronization-completion event to Member 5.

## 13. Definition of Done

- Both the real and fixture providers run successfully.
- Three interfaces, two tables, one view, and tests pass.
- Automated tests cover locking, retry, idempotency, and degradation.
