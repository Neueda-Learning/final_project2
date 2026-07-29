package com.portfoliomanager.worker.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfoliomanager.worker.MarketDataProperties;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(
        named = "TWELVE_DATA_API_KEY",
        matches = ".+")
class TwelveDataLiveIntegrationTest {

    @Test
    void fetchesARealDailyCloseFromTwelveData() {
        var provider = new TwelveDataProvider(properties());

        var prices = provider.fetchDailyCloses(
                List.of("AAPL"),
                LocalDate.now().minusDays(14),
                LocalDate.now().plusDays(1));

        assertThat(prices).isNotEmpty();
        assertThat(prices).allSatisfy(price -> {
            assertThat(price.symbol()).isEqualTo("AAPL");
            assertThat(price.closePrice()).isPositive();
            assertThat(price.currency()).isEqualTo("USD");
            assertThat(price.source()).isEqualTo("twelve-data");
        });
    }

    @Test
    void searchesRealInstrumentsFromTwelveData() {
        var provider = new TwelveDataProvider(properties());

        assertThat(provider.searchInstruments("AAPL", 10))
                .anySatisfy(asset -> {
                    assertThat(asset.symbol()).isEqualTo("AAPL");
                    assertThat(asset.name()).containsIgnoringCase("Apple");
                });
    }

    @Test
    void fetchesRealIntradayBarsFromTwelveData() {
        var provider = new TwelveDataProvider(properties());
        LocalDateTime end = LocalDateTime.now(ZoneOffset.UTC);

        assertThat(provider.fetchIntradayBars(
                        "AAPL", "1min", end.minusDays(5), end))
                .isNotEmpty()
                .allSatisfy(bar -> {
                    assertThat(bar.symbol()).isEqualTo("AAPL");
                    assertThat(bar.interval()).isEqualTo("1min");
                    assertThat(bar.closePrice()).isPositive();
                    assertThat(bar.source()).isEqualTo("twelve-data");
                });
    }

    private MarketDataProperties properties() {
        var properties = new MarketDataProperties();
        properties.setApiKey(System.getenv("TWELVE_DATA_API_KEY"));
        properties.setRequestTimeoutSeconds(20);
        properties.setTwelveDataRequestIntervalMillis(0);
        return properties;
    }
}
