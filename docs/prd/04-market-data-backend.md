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
| Status | Implemented (Alpaca primary; Twelve Data fallback) |

## 2. Module Goal

Fetch daily OHLCV history for stocks and ETFs from a real external provider,
persist it idempotently in MySQL, and use daily closes for portfolio valuation.

## 3. Market-Data Definition

- Portfolio valuation and historical charts use stored daily closes.
- Trade entry uses the user's actual execution price and does not depend on market data.
- The default real provider is Alpaca Market Data; Twelve Data is the fallback.
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
3. Query active instruments for the tradable universe.
4. Request approximately one month of daily history with timeouts, rate limiting, and retries.
5. Validate symbol, date, currency, and positive price.
6. Upsert daily OHLCV rows idempotently.
7. Record successful, failed, and categorized errors.
8. Mark the run `SUCCEEDED`, `PARTIAL`, or `FAILED`.
9. Notify Member 5 to generate snapshots for affected portfolios.
10. Call `RELEASE_LOCK()` in `finally`.

The Alpaca implementation requests up to 50 instruments per daily-bars batch,
follows `next_page_token`, and permits four concurrent network tasks while a
client-side limiter keeps request starts below the Basic-plan ceiling. If Alpaca
fails or omits a symbol, the failover adapter requests that symbol from Twelve
Data. Twelve Data stays single-symbol and serial because its historical
multi-symbol responses can truncate the requested range and its basic quota is
substantially lower. Synchronization requests approximately one month of history
and replays historical transactions to rebuild daily valuation snapshots.

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
| `MARKET_DATA_PROVIDER` | `alpaca` (default), `twelve-data`, or `fixture` |
| `MARKET_SYNC_CRON` | Schedule expression |
| `MARKET_TIMEZONE` | Market time zone |
| `MARKET_BATCH_SIZE` | Alpaca daily-bars batch size; defaults to `50` |
| `MARKET_REQUEST_CONCURRENCY` | Maximum concurrent provider requests; defaults to `4` |
| `MARKET_REQUEST_TIMEOUT_SECONDS` | Request timeout |
| `MARKET_MAX_RETRIES` | Maximum retries |
| `ALPACA_API_BASE_URL` | Paper trading/assets base URL |
| `ALPACA_DATA_BASE_URL` | Market Data API base URL |
| `ALPACA_API_KEY_ID` / `ALPACA_API_SECRET_KEY` | Alpaca credentials |
| `ALPACA_DATA_FEED` | Alpaca stock feed; defaults to `iex` |
| `ALPACA_REQUESTS_PER_MINUTE` | Alpaca client ceiling; defaults to `180` |
| `TWELVE_DATA_API_KEY` | Optional fallback credential |
| `TWELVE_DATA_REQUEST_INTERVAL_MILLIS` | Twelve Data request spacing; defaults to `8000` |

The defaults use Alpaca with Twelve Data failover, the `America/New_York` market
calendar, and one sync after the trading day. Supply credentials and overrides
through environment variables.

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
- Daily-close history returns only stored real daily closes in descending date order and enforces its limit.

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
