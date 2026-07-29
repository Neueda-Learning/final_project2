package com.portfoliomanager.worker.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.portfoliomanager.worker.MarketDataProperties;
import org.junit.jupiter.api.Test;

class AlpacaMarketDataProviderTest {

    private final AlpacaMarketDataProvider provider =
            new AlpacaMarketDataProvider(new MarketDataProperties());

    @Test
    void normalizesMultiSymbolDailyBars() {
        String response =
                """
                {
                  "bars": {
                    "AAPL": [{
                      "t": "2026-07-28T04:00:00Z",
                      "o": 212.10,
                      "h": 215.00,
                      "l": 211.50,
                      "c": 213.55,
                      "v": 43210000
                    }],
                    "SPY": [{
                      "t": "2026-07-28T04:00:00Z",
                      "o": 635.10,
                      "h": 638.00,
                      "l": 634.50,
                      "c": 637.10,
                      "v": 50100000
                    }]
                  },
                  "next_page_token": null
                }
                """;

        assertThat(provider.parseDailyBars(response))
                .hasSize(2)
                .filteredOn(price -> price.symbol().equals("AAPL"))
                .singleElement()
                .satisfies(price -> {
                    assertThat(price.priceDate().toString()).isEqualTo("2026-07-28");
                    assertThat(price.closePrice()).isEqualByComparingTo("213.55");
                    assertThat(price.adjustedClose()).isEqualByComparingTo("213.55");
                    assertThat(price.volume()).isEqualTo(43_210_000L);
                    assertThat(price.source()).isEqualTo("alpaca");
                });
    }

    @Test
    void normalizesIntradayBarsToUtc() {
        String response =
                """
                {
                  "bars": [{
                    "t": "2026-07-28T19:59:00Z",
                    "o": 214.10,
                    "h": 214.30,
                    "l": 214.00,
                    "c": 214.25,
                    "v": 1200
                  }],
                  "symbol": "AAPL",
                  "next_page_token": null
                }
                """;

        assertThat(provider.parseIntradayBars(response, "AAPL", "1min"))
                .singleElement()
                .satisfies(bar -> {
                    assertThat(bar.symbol()).isEqualTo("AAPL");
                    assertThat(bar.interval()).isEqualTo("1min");
                    assertThat(bar.timestamp().toString())
                            .isEqualTo("2026-07-28T19:59");
                    assertThat(bar.closePrice()).isEqualByComparingTo("214.25");
                    assertThat(bar.source()).isEqualTo("alpaca");
                });
    }

    @Test
    void normalizesAssetsForInstrumentSearch() {
        String response =
                """
                [{
                  "symbol": "AAPL",
                  "name": "Apple Inc.",
                  "exchange": "NASDAQ",
                  "class": "us_equity",
                  "status": "active",
                  "tradable": true
                }]
                """;

        assertThat(provider.parseAssets(response)).singleElement().satisfies(asset -> {
            assertThat(asset.symbol()).isEqualTo("AAPL");
            assertThat(asset.name()).isEqualTo("Apple Inc.");
            assertThat(asset.currency()).isEqualTo("USD");
            assertThat(asset.instrumentType()).isEqualTo("us_equity");
        });
    }

    @Test
    void exposesAlpacaErrors() {
        assertThatThrownBy(() -> provider.parseDailyBars(
                        """
                        {"code":42910000,"message":"rate limit exceeded"}
                        """))
                .isInstanceOf(MarketDataProviderException.class)
                .hasMessageContaining("rate limit");
    }

    @Test
    void treatsJsonNullPageTokenAsEndOfPagination() {
        assertThat(provider.nextPageToken(
                        """
                        {"bars":{},"next_page_token":null}
                        """))
                .isNull();
    }
}
