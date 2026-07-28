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
}
