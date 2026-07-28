package com.portfoliomanager.worker.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class FixtureMarketDataProviderTest {

    private final FixtureMarketDataProvider provider = new FixtureMarketDataProvider();

    @Test
    void returnsDeterministicPositivePricesForOfflineRuns() {
        var first = provider.fetchDailyCloses(
                List.of("AAPL", "SPY"),
                LocalDate.of(2026, 7, 20),
                LocalDate.of(2026, 7, 28));
        var second = provider.fetchDailyCloses(
                List.of("AAPL", "SPY"),
                LocalDate.of(2026, 7, 20),
                LocalDate.of(2026, 7, 28));

        assertThat(first).isEqualTo(second);
        assertThat(first).hasSize(2);
        assertThat(first).allSatisfy(price -> {
            assertThat(price.closePrice()).isPositive();
            assertThat(price.source()).isEqualTo("fixture");
            assertThat(price.currency()).isEqualTo("USD");
        });
    }

    @Test
    void doesNotReturnWeekendPriceDates() {
        var prices = provider.fetchDailyCloses(
                List.of("AAPL"),
                LocalDate.of(2026, 7, 20),
                LocalDate.of(2026, 7, 27));

        assertThat(prices).singleElement()
                .extracting(DailyPrice::priceDate)
                .isEqualTo(LocalDate.of(2026, 7, 24));
    }
}
