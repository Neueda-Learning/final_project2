package com.portfoliomanager.worker.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.portfoliomanager.worker.MarketDataProperties;
import org.junit.jupiter.api.Test;

class TwelveDataProviderTest {

    private final TwelveDataProvider provider =
            new TwelveDataProvider(new MarketDataProperties());

    @Test
    void normalizesSingleSymbolResponse() {
        String response =
                """
                {
                  "meta": {"symbol": "AAPL", "currency": "USD"},
                  "values": [{
                    "datetime": "2026-07-24",
                    "open": "212.10",
                    "high": "215.00",
                    "low": "211.50",
                    "close": "213.55",
                    "volume": "43210000"
                  }],
                  "status": "ok"
                }
                """;

        assertThat(provider.parseTimeSeries(response)).singleElement().satisfies(price -> {
            assertThat(price.symbol()).isEqualTo("AAPL");
            assertThat(price.closePrice()).isEqualByComparingTo("213.55");
            assertThat(price.adjustedClose()).isEqualByComparingTo("213.55");
            assertThat(price.volume()).isEqualTo(43_210_000L);
        });
    }

    @Test
    void normalizesMultiSymbolResponse() {
        String response =
                """
                {
                  "AAPL": {
                    "meta": {"symbol": "AAPL", "currency": "USD"},
                    "values": [{"datetime": "2026-07-24", "close": "213.55"}],
                    "status": "ok"
                  },
                  "SPY": {
                    "meta": {"symbol": "SPY", "currency": "USD"},
                    "values": [{"datetime": "2026-07-24", "close": "637.10"}],
                    "status": "ok"
                  }
                }
                """;

        assertThat(provider.parseTimeSeries(response))
                .extracting(DailyPrice::symbol)
                .containsExactlyInAnyOrder("AAPL", "SPY");
    }

    @Test
    void exposesProviderErrors() {
        assertThatThrownBy(() -> provider.parseTimeSeries(
                        """
                        {"status":"error","message":"rate limit exceeded"}
                        """))
                .isInstanceOf(MarketDataProviderException.class)
                .hasMessageContaining("rate limit");
    }

    @Test
    void normalizesOneMinuteBarsInUtc() {
        String response =
                """
                {
                  "meta": {"symbol": "AAPL", "currency": "USD"},
                  "values": [{
                    "datetime": "2026-07-27 19:59:00",
                    "open": "214.10",
                    "high": "214.30",
                    "low": "214.00",
                    "close": "214.25",
                    "volume": "1200"
                  }],
                  "status": "ok"
                }
                """;

        assertThat(provider.parseIntradaySeries(response, "1min"))
                .singleElement()
                .satisfies(bar -> {
                    assertThat(bar.interval()).isEqualTo("1min");
                    assertThat(bar.timestamp().toString())
                            .isEqualTo("2026-07-27T19:59");
                    assertThat(bar.closePrice()).isEqualByComparingTo("214.25");
                    assertThat(bar.volume()).isEqualTo(1_200L);
                });
    }
}
