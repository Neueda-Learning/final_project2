package com.portfoliomanager.worker.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfoliomanager.worker.MarketDataProperties;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

class TwelveDataLiveIntegrationTest {

    @Test
    @EnabledIfEnvironmentVariable(
            named = "TWELVE_DATA_API_KEY",
            matches = ".+")
    void fetchesARealDailyCloseFromTwelveData() {
        var properties = new MarketDataProperties();
        properties.setApiKey(System.getenv("TWELVE_DATA_API_KEY"));
        properties.setRequestTimeoutSeconds(20);
        var provider = new TwelveDataProvider(properties);

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
}
