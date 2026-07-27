package com.portfoliomanager.worker.provider;

import com.portfoliomanager.worker.MarketDataProperties;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TwelveDataProvider implements MarketDataProvider {

    private final RestClient client;
    private final MarketDataProperties properties;

    public TwelveDataProvider(MarketDataProperties properties) {
        this.properties = properties;
        this.client = RestClient.builder()
                .baseUrl("https://api.twelvedata.com")
                .build();
    }

    @Override
    public String name() {
        return "twelve-data";
    }

    @Override
    public List<DailyPrice> getDailyPrices(
            List<String> symbols,
            LocalDate start,
            LocalDate end) {
        if (properties.getApiKey().isBlank() || symbols.isEmpty()) {
            return List.of();
        }

        // Provider response normalization and persistence are the market-data phase's
        // implementation boundary. Keeping the HTTP client here prevents domain services
        // from depending on an external vendor.
        client.get()
                .uri(uri -> uri.path("/time_series")
                        .queryParam("symbol", String.join(",", symbols))
                        .queryParam("interval", "1day")
                        .queryParam("start_date", start)
                        .queryParam("end_date", end)
                        .queryParam("apikey", properties.getApiKey())
                        .build())
                .retrieve()
                .toBodilessEntity();
        return List.of();
    }
}
