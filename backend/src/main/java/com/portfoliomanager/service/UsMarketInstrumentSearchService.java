package com.portfoliomanager.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class UsMarketInstrumentSearchService {

    private static final Logger log = LoggerFactory.getLogger(UsMarketInstrumentSearchService.class);
    private static final Duration ALPACA_ASSET_CACHE_TTL = Duration.ofMinutes(15);

    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final String provider;
    private final String alpacaApiBaseUrl;
    private final String alpacaApiKeyId;
    private final String alpacaApiSecretKey;
    private final String twelveDataApiKey;

    private final Object alpacaAssetCacheLock = new Object();
    private volatile List<DiscoveredInstrument> cachedAlpacaAssets = List.of();
    private volatile Instant alpacaCacheExpiresAt = Instant.EPOCH;

    public UsMarketInstrumentSearchService(
            @Value("${market-data.provider:alpaca}") String provider,
            @Value("${market-data.alpaca-api-base-url:${ALPACA_API_BASE_URL:https://paper-api.alpaca.markets/v2}}")
                    String alpacaApiBaseUrl,
            @Value("${market-data.alpaca-api-key-id:${ALPACA_API_KEY_ID:}}")
                    String alpacaApiKeyId,
            @Value("${market-data.alpaca-api-secret-key:${ALPACA_API_SECRET_KEY:}}")
                    String alpacaApiSecretKey,
            @Value("${market-data.api-key:${TWELVE_DATA_API_KEY:}}")
                    String twelveDataApiKey) {
        this.client = RestClient.builder().build();
        this.objectMapper = new ObjectMapper();
        this.provider = provider;
        this.alpacaApiBaseUrl = alpacaApiBaseUrl;
        this.alpacaApiKeyId = alpacaApiKeyId;
        this.alpacaApiSecretKey = alpacaApiSecretKey;
        this.twelveDataApiKey = twelveDataApiKey;
    }

    public List<DiscoveredInstrument> search(String query, int limit) {
        if (limit <= 0 || query == null || query.isBlank()) {
            return List.of();
        }

        String normalizedQuery = query.trim().toUpperCase(Locale.ROOT);
        try {
            if ("twelve-data".equalsIgnoreCase(provider) && hasTwelveDataKey()) {
                return searchTwelveData(normalizedQuery, limit);
            }
            if (hasAlpacaCredentials()) {
                return searchAlpacaAssets(normalizedQuery, limit);
            }
            if (hasTwelveDataKey()) {
                return searchTwelveData(normalizedQuery, limit);
            }
        } catch (RuntimeException exception) {
            log.warn("External US market search failed: {}", rootMessage(exception));
        }

        return List.of();
    }

    private List<DiscoveredInstrument> searchAlpacaAssets(String normalizedQuery, int limit) {
        return loadAlpacaAssets().stream()
                .filter(instrument -> matchesQuery(instrument, normalizedQuery))
                .limit(limit)
                .toList();
    }

    private List<DiscoveredInstrument> loadAlpacaAssets() {
        Instant now = Instant.now();
        if (now.isBefore(alpacaCacheExpiresAt)) {
            return cachedAlpacaAssets;
        }

        synchronized (alpacaAssetCacheLock) {
            now = Instant.now();
            if (now.isBefore(alpacaCacheExpiresAt)) {
                return cachedAlpacaAssets;
            }

            URI uri = UriComponentsBuilder.fromUriString(alpacaApiBaseUrl)
                    .path("/assets")
                    .queryParam("status", "active")
                    .queryParam("asset_class", "us_equity")
                    .build()
                    .encode()
                    .toUri();

            String response = client.get()
                    .uri(uri)
                    .header("APCA-API-KEY-ID", alpacaApiKeyId)
                    .header("APCA-API-SECRET-KEY", alpacaApiSecretKey)
                    .retrieve()
                    .body(String.class);

            List<DiscoveredInstrument> parsed = parseAlpacaAssets(response);
            cachedAlpacaAssets = List.copyOf(parsed);
            alpacaCacheExpiresAt = now.plus(ALPACA_ASSET_CACHE_TTL);
            return cachedAlpacaAssets;
        }
    }

    private List<DiscoveredInstrument> parseAlpacaAssets(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            if (!root.isArray()) {
                return List.of();
            }

            List<DiscoveredInstrument> instruments = new ArrayList<>();
            for (JsonNode item : root) {
                String symbol = item.path("symbol").asText("").trim().toUpperCase(Locale.ROOT);
                String name = item.path("name").asText("").trim();
                if (symbol.isBlank() || name.isBlank()) {
                    continue;
                }

                String exchangeCode = item.path("exchange").asText("US").trim().toUpperCase(Locale.ROOT);
                if (exchangeCode.isBlank()) {
                    exchangeCode = "US";
                }
                String assetClass = item.path("class").asText("us_equity");

                instruments.add(new DiscoveredInstrument(
                        symbol,
                        name,
                        exchangeCode,
                        "USD",
                        inferAssetType(name, assetClass),
                        symbol));
            }
            return instruments;
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid Alpaca assets response", exception);
        }
    }

    private List<DiscoveredInstrument> searchTwelveData(String normalizedQuery, int limit) {
        URI uri = UriComponentsBuilder
                .fromUriString("https://api.twelvedata.com/symbol_search")
                .queryParam("symbol", normalizedQuery)
                .queryParam("outputsize", limit)
                .build()
                .encode()
                .toUri();

        String response = client.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "apikey " + twelveDataApiKey)
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.path("data");
            if (!data.isArray()) {
                return List.of();
            }

            List<DiscoveredInstrument> results = new ArrayList<>();
            for (JsonNode item : data) {
                String symbol = item.path("symbol").asText("").trim().toUpperCase(Locale.ROOT);
                String name = item.path("instrument_name").asText("").trim();
                if (symbol.isBlank() || name.isBlank()) {
                    continue;
                }

                String country = item.path("country").asText("").trim();
                String currency = item.path("currency").asText("USD").trim().toUpperCase(Locale.ROOT);
                if (!country.isBlank() && !"UNITED STATES".equalsIgnoreCase(country)) {
                    continue;
                }
                if (!"USD".equals(currency)) {
                    continue;
                }

                String exchangeCode = item.path("exchange").asText("US").trim().toUpperCase(Locale.ROOT);
                if (exchangeCode.isBlank()) {
                    exchangeCode = "US";
                }

                String instrumentType = item.path("instrument_type").asText("");
                results.add(new DiscoveredInstrument(
                        symbol,
                        name,
                        exchangeCode,
                        currency,
                        inferAssetType(name, instrumentType),
                        symbol));
            }
            return results.stream()
                    .filter(instrument -> matchesQuery(instrument, normalizedQuery))
                    .limit(limit)
                    .toList();
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid Twelve Data symbol search response", exception);
        }
    }

    private boolean matchesQuery(DiscoveredInstrument instrument, String normalizedQuery) {
        String symbol = instrument.symbol().toUpperCase(Locale.ROOT);
        String name = instrument.name().toUpperCase(Locale.ROOT);
        return symbol.contains(normalizedQuery) || name.contains(normalizedQuery);
    }

    private String inferAssetType(String name, String providerType) {
        String normalizedType = providerType == null ? "" : providerType.trim().toUpperCase(Locale.ROOT);
        if (normalizedType.contains("ETF")) {
            return "ETF";
        }

        String normalizedName = name.toUpperCase(Locale.ROOT);
        if (normalizedName.contains(" ETF")
                || normalizedName.startsWith("ETF ")
                || normalizedName.contains("EXCHANGE TRADED FUND")) {
            return "ETF";
        }

        return "STOCK";
    }

    private boolean hasAlpacaCredentials() {
        return !alpacaApiKeyId.isBlank() && !alpacaApiSecretKey.isBlank();
    }

    private boolean hasTwelveDataKey() {
        return !twelveDataApiKey.isBlank();
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }

    public record DiscoveredInstrument(
            String symbol,
            String name,
            String exchangeCode,
            String currency,
            String assetType,
            String providerSymbol) {}
}
