# Market-Data Provider Benchmark

## Scope

This benchmark was executed on 2026-07-29 against the local
`portfolio_manager_local` database and the 39 instruments with active portfolio
positions. It used the production synchronization path:

- Approximately one month of adjusted daily OHLCV history
- Five days of one-minute OHLCV history
- Validation, idempotent upserts, current valuation refresh, and historical
  valuation snapshot rebuild
- Alpaca Basic IEX feed, batch size 50, concurrency 4, client ceiling 180 RPM
- Twelve Data single-symbol requests with 8-second spacing

Alpaca's paper-account API is used for the asset catalog, while historical bars
come from the separate Market Data API domain.

## Full Synchronization Result

| Metric | Twelve Data | Alpaca |
|---|---:|---:|
| Run ID | `283ea8c0-44b9-4bb6-bde2-96bedc606522` | `81655e22-ad99-4e2e-8ea0-b664338ec1d0` |
| Status | `SUCCEEDED` | `SUCCEEDED` |
| Instruments | 39/39 | 39/39 |
| Failed instruments | 0 | 0 |
| Core sync duration | 665.244 s | 19.260 s |
| Relative speed | 1.00x | 34.54x |
| Estimated provider calls | 78 | 40 |
| Daily rows in the common window | 780 | 780 |
| One-minute rows in the compared window | 43,190 | 33,451 |
| Rate-limit or final error | None | None |

Alpaca reduced full synchronization time by approximately 97.1%. Its 40 calls
were one multi-symbol daily request plus one intraday request per instrument;
none of the responses required another page. Twelve Data required one daily and
one intraday call per symbol.

A second recent Twelve Data run took 983.781 seconds and recorded a transient
connection reset for VOO, even though all 39 symbols ultimately succeeded. Two
Alpaca runs took 17.399 and 19.260 seconds.

## Data Differences

Both providers returned all 780 daily bars in the common date range.

| Daily comparison over 780 matching bars | Result |
|---|---:|
| Mean absolute close difference | 3.7777 bps |
| Maximum absolute close difference | 123.9157 bps |
| Bars differing by more than 10 bps | 43 |
| Mean Alpaca volume as a share of Twelve Data volume | 4.32% |

The five largest daily close differences were all for the low-volume IPO ETF;
the maximum occurred on 2026-07-08. This is consistent with comparing Alpaca's
single-exchange IEX feed with a broader market feed.

| One-minute comparison | Result |
|---|---:|
| Exact timestamp overlaps | 32,809 |
| Alpaca bars | 33,451 |
| Twelve Data bars in the same time window | 43,190 |
| Mean absolute close difference on overlaps | 1.6151 bps |
| Maximum absolute close difference | 57.7712 bps |
| Overlaps differing by more than 10 bps | 549 |
| Mean Alpaca volume as a share of Twelve Data volume | 7.16% |

Alpaca minute coverage was approximately 77.5% of the Twelve Data row count in
the compared window. Coverage varied by liquidity: IPO returned 11 Alpaca bars
versus 92 Twelve Data bars, while actively traded instruments were much closer.
Provider source is therefore retained on every row.

## Direct Endpoint Latency

These figures are single local observations and include network transfer and
JSON parsing.

| Provider endpoint | HTTP | Rows | Latency |
|---|---:|---:|---:|
| Alpaca active US assets | 200 | 14,153 | 3,133 ms |
| Alpaca daily bars, all 39 symbols | 200 | 780 | 1,215 ms |
| Alpaca AAPL one-minute bars | 200 | 1,215 | 1,124 ms |
| Twelve Data symbol search | 200 | 10 | 920 ms |
| Twelve Data AAPL daily bars | 200 | 7 | 557 ms |
| Twelve Data AAPL one-minute bars | 200 | 1,170 | 2,066 ms |

Alpaca's asset list is cached for 15 minutes. Daily synchronization benefits
from its multi-symbol endpoint; Twelve Data remains single-symbol because its
historical multi-symbol response can truncate the requested range.

## Interface Verification Matrix

| Interface | Result |
|---|---|
| Alpaca `GET /v2/assets` | Passed against paper API |
| Alpaca `GET /v2/stocks/bars` | Passed with two-symbol test and 39-symbol benchmark |
| Alpaca `GET /v2/stocks/{symbol}/bars` | Passed with real one-minute history |
| Twelve Data `GET /symbol_search` | Passed |
| Twelve Data `GET /time_series` daily | Passed |
| Twelve Data `GET /time_series` intraday | Passed |
| `POST /api/v1/market-data/sync` | 202, Alpaca `RUNNING` contract verified in a rolled-back real-DB test |
| `GET /api/v1/market-data/sync-runs/latest` | 200, Alpaca 39/39 success verified |
| `GET /api/v1/instruments` | 200, active AAPL search verified |
| `GET /api/v1/instruments/{id}/latest-price` | 200, latest Alpaca source verified |
| `GET /api/v1/instruments/{id}/tradable-prices` | 200, limit and source verified |
| `GET /api/v1/instruments/{id}/bars` | 200, paging and cross-provider timestamp de-duplication verified |
| Trading daily-price lookup | Latest Alpaca source verified |
| Trading exact-minute lookup | Latest Alpaca source verified |
| Invalid instrument, interval, range, and limit | Expected 404/422 responses verified |

## Decision

Alpaca is the default because it materially improves synchronization latency,
supports multi-symbol history, paginates explicitly, and has a documented Basic
allowance of 200 historical calls per minute. The client is capped at 180 RPM to
retain headroom.

Twelve Data remains valuable as a per-symbol fallback because the IEX feed has
lower volume and can have sparse minute coverage for less-liquid ETFs. Primary
failures and missing symbols fall back to Twelve Data, while normal Alpaca
traffic does not consume its lower request allowance.

Official references:

- [Alpaca historical bars](https://docs.alpaca.markets/us/reference/stockbars)
- [Alpaca market-data plans](https://docs.alpaca.markets/us/docs/about-market-data-api)
- [Alpaca authentication](https://docs.alpaca.markets/us/v1.4.2/docs/authentication)
- [Alpaca assets](https://docs.alpaca.markets/us/reference/get-v2-assets-1)
