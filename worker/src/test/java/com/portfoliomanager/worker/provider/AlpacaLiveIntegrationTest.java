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
        named = "ALPACA_API_KEY_ID",
        matches = ".+")
@EnabledIfEnvironmentVariable(
        named = "ALPACA_API_SECRET_KEY",
        matches = ".+")
class AlpacaLiveIntegrationTest {

    @Test
    void fetchesRecentIexDailyBars() {
        MarketDataProperties properties = properties();
        AlpacaMarketDataProvider provider =
                new AlpacaMarketDataProvider(properties);
        LocalDate end = LocalDate.now().plusDays(1);

        assertThat(provider.fetchDailyCloses(
                        List.of("AAPL", "SPY"), end.minusDays(10), end))
                .isNotEmpty()
                .allSatisfy(price -> {
                    assertThat(price.symbol()).isIn("AAPL", "SPY");
                    assertThat(price.source()).isEqualTo("alpaca");
                    assertThat(price.closePrice()).isPositive();
                })
                .extracting(DailyPrice::symbol)
                .contains("AAPL", "SPY");
    }

    @Test
    void searchesThePaperAccountAssetCatalog() {
        MarketDataProperties properties = properties();
        AlpacaMarketDataProvider provider =
                new AlpacaMarketDataProvider(properties);

        assertThat(provider.searchInstruments("Apple", 10))
                .anySatisfy(asset -> {
                    assertThat(asset.symbol()).isEqualTo("AAPL");
                    assertThat(asset.name()).containsIgnoringCase("Apple");
                });
    }

    @Test
    void fetchesRecentIexIntradayBars() {
        MarketDataProperties properties = properties();
        AlpacaMarketDataProvider provider =
                new AlpacaMarketDataProvider(properties);
        LocalDateTime end = LocalDateTime.now(ZoneOffset.UTC);

        assertThat(provider.fetchIntradayBars(
                        "AAPL", "1min", end.minusDays(10), end))
                .isNotEmpty()
                .allSatisfy(bar -> {
                    assertThat(bar.symbol()).isEqualTo("AAPL");
                    assertThat(bar.interval()).isEqualTo("1min");
                    assertThat(bar.source()).isEqualTo("alpaca");
                    assertThat(bar.closePrice()).isPositive();
                });
    }

    private MarketDataProperties properties() {
        MarketDataProperties properties = new MarketDataProperties();
        properties.setAlpacaApiKeyId(System.getenv("ALPACA_API_KEY_ID"));
        properties.setAlpacaApiSecretKey(System.getenv("ALPACA_API_SECRET_KEY"));
        properties.setAlpacaFeed("iex");
        return properties;
    }
}
