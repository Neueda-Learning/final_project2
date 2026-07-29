package com.portfoliomanager.worker.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfoliomanager.worker.MarketDataProperties;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(
        name = "market-data.provider",
        havingValue = "twelve-data")
public class TwelveDataProvider implements MarketDataProvider {

    private final RestClient client;
    private final MarketDataProperties properties;
    private final ObjectMapper objectMapper;
    private final RequestRateLimiter rateLimiter;

    public TwelveDataProvider(MarketDataProperties properties) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
        this.rateLimiter = RequestRateLimiter.fixedInterval(
                Duration.ofMillis(properties.getTwelveDataRequestIntervalMillis()));
        Duration timeout = Duration.ofSeconds(properties.getRequestTimeoutSeconds());
        var httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        this.client = RestClient.builder()
                .baseUrl("https://api.twelvedata.com")
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public String name() {
        return "twelve-data";
    }

    @Override
    public List<InstrumentSearchResult> searchInstruments(String query, int limit) {
        requireApiKey();
        try {
            rateLimiter.acquire();
            String response = client.get()
                    .uri(uri -> uri.path("/symbol_search")
                            .queryParam("symbol", query)
                            .queryParam("outputsize", limit)
                            .build())
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "apikey " + properties.getApiKey())
                    .retrieve()
                    .body(String.class);
            JsonNode data = objectMapper.readTree(response).path("data");
            if (!data.isArray()) {
                return List.of();
            }
            List<InstrumentSearchResult> results = new ArrayList<>();
            for (JsonNode item : data) {
                results.add(new InstrumentSearchResult(
                        item.path("symbol").asText(),
                        item.path("instrument_name").asText(),
                        item.path("exchange").asText(),
                        item.path("currency").asText("USD"),
                        item.path("instrument_type").asText()));
            }
            return results;
        } catch (Exception exception) {
            throw new MarketDataProviderException(
                    "Twelve Data instrument search failed", exception);
        }
    }

    @Override
    public List<DailyPrice> fetchDailyCloses(
            List<String> symbols,
            LocalDate start,
            LocalDate end) {
        if (symbols.isEmpty()) {
            return List.of();
        }
        requireApiKey();
        try {
            rateLimiter.acquire();
            String response = client.get()
                    .uri(uri -> uri.path("/time_series")
                            .queryParam("symbol", String.join(",", symbols))
                            .queryParam("interval", "1day")
                            .queryParam("start_date", start)
                            .queryParam("end_date", end)
                            .queryParam("order", "ASC")
                            .queryParam("adjust", "all")
                            .build())
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "apikey " + properties.getApiKey())
                    .retrieve()
                    .body(String.class);
            return parseTimeSeries(response);
        } catch (MarketDataProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new MarketDataProviderException("Twelve Data request failed", exception);
        }
    }

    @Override
    public List<IntradayBar> fetchIntradayBars(
            String symbol,
            String interval,
            LocalDateTime start,
            LocalDateTime end) {
        requireApiKey();
        try {
            rateLimiter.acquire();
            String response = client.get()
                    .uri(uri -> uri.path("/time_series")
                            .queryParam("symbol", symbol)
                            .queryParam("interval", interval)
                            .queryParam("start_date", formatTimestamp(start))
                            .queryParam("end_date", formatTimestamp(end))
                            .queryParam("timezone", "UTC")
                            .queryParam("order", "ASC")
                            .queryParam("outputsize", 5000)
                            .build())
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "apikey " + properties.getApiKey())
                    .retrieve()
                    .body(String.class);
            return parseIntradaySeries(response, interval);
        } catch (MarketDataProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new MarketDataProviderException(
                    "Twelve Data intraday request failed", exception);
        }
    }

    List<IntradayBar> parseIntradaySeries(String response, String interval) {
        try {
            JsonNode root = objectMapper.readTree(response);
            if ("error".equalsIgnoreCase(root.path("status").asText())) {
                throw new MarketDataProviderException(
                        "Twelve Data error: " + root.path("message").asText("unknown error"));
            }
            JsonNode meta = root.path("meta");
            String symbol = meta.path("symbol").asText();
            String currency = meta.path("currency").asText("USD");
            List<IntradayBar> bars = new ArrayList<>();
            for (JsonNode value : root.path("values")) {
                bars.add(new IntradayBar(
                        symbol,
                        interval,
                        parseTimestamp(value.path("datetime").asText()),
                        decimal(value, "open"),
                        decimal(value, "high"),
                        decimal(value, "low"),
                        decimal(value, "close"),
                        value.hasNonNull("volume")
                                ? Long.valueOf(value.path("volume").asText())
                                : null,
                        currency,
                        name()));
            }
            return bars;
        } catch (MarketDataProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new MarketDataProviderException(
                    "Invalid Twelve Data intraday response", exception);
        }
    }

    private static String formatTimestamp(LocalDateTime value) {
        return value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private static LocalDateTime parseTimestamp(String value) {
        return value.contains("T")
                ? LocalDateTime.parse(value)
                : LocalDateTime.parse(
                        value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    @Override
    public boolean healthCheck() {
        return !properties.getApiKey().isBlank();
    }

    List<DailyPrice> parseTimeSeries(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            if ("error".equalsIgnoreCase(root.path("status").asText())) {
                throw new MarketDataProviderException(
                        "Twelve Data error: " + root.path("message").asText("unknown error"));
            }

            List<DailyPrice> prices = new ArrayList<>();
            if (root.has("meta") && root.has("values")) {
                appendSeries(root, null, prices);
                return prices;
            }

            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                if (field.getValue().has("values")) {
                    appendSeries(field.getValue(), field.getKey(), prices);
                }
            }
            return prices;
        } catch (MarketDataProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new MarketDataProviderException(
                    "Invalid Twelve Data response", exception);
        }
    }

    private void appendSeries(
            JsonNode series, String fallbackSymbol, List<DailyPrice> prices) {
        JsonNode meta = series.path("meta");
        String symbol = meta.path("symbol").asText(fallbackSymbol);
        String currency = meta.path("currency").asText("USD");
        for (JsonNode value : series.path("values")) {
            BigDecimal close = decimal(value, "close");
            prices.add(new DailyPrice(
                    symbol,
                    LocalDate.parse(value.path("datetime").asText()),
                    nullableDecimal(value, "open"),
                    nullableDecimal(value, "high"),
                    nullableDecimal(value, "low"),
                    close,
                    value.hasNonNull("adjusted_close")
                            ? decimal(value, "adjusted_close")
                            : close,
                    value.hasNonNull("volume")
                            ? Long.valueOf(value.path("volume").asText())
                            : null,
                    currency,
                    name(),
                    null));
        }
    }

    private BigDecimal decimal(JsonNode node, String field) {
        return new BigDecimal(node.path(field).asText());
    }

    private BigDecimal nullableDecimal(JsonNode node, String field) {
        return node.hasNonNull(field) ? decimal(node, field) : null;
    }

    private void requireApiKey() {
        if (properties.getApiKey().isBlank()) {
            throw new MarketDataProviderException(
                    "TWELVE_DATA_API_KEY is required for the twelve-data provider");
        }
    }
}
