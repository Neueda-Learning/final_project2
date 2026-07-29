package com.portfoliomanager.worker.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfoliomanager.worker.MarketDataProperties;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

public class AlpacaMarketDataProvider implements MarketDataProvider {

    private static final Logger log =
            LoggerFactory.getLogger(AlpacaMarketDataProvider.class);
    private static final int PAGE_LIMIT = 10_000;
    private static final Duration ASSET_CACHE_TTL = Duration.ofMinutes(15);

    private final RestClient client;
    private final MarketDataProperties properties;
    private final ObjectMapper objectMapper;
    private final RequestRateLimiter rateLimiter;
    private final Object assetCacheLock = new Object();

    private volatile List<InstrumentSearchResult> cachedAssets = List.of();
    private volatile Instant assetCacheExpiresAt = Instant.EPOCH;

    public AlpacaMarketDataProvider(MarketDataProperties properties) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
        this.rateLimiter =
                RequestRateLimiter.perMinute(properties.getAlpacaRequestsPerMinute());
        Duration timeout = Duration.ofSeconds(properties.getRequestTimeoutSeconds());
        var httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        this.client = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public String name() {
        return "alpaca";
    }

    @Override
    public List<InstrumentSearchResult> searchInstruments(String query, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        String normalizedQuery = query == null
                ? ""
                : query.trim().toUpperCase(Locale.ROOT);
        return loadAssets().stream()
                .filter(asset -> normalizedQuery.isEmpty()
                        || asset.symbol().toUpperCase(Locale.ROOT).contains(normalizedQuery)
                        || asset.name().toUpperCase(Locale.ROOT).contains(normalizedQuery))
                .limit(limit)
                .toList();
    }

    @Override
    public List<DailyPrice> fetchDailyCloses(
            List<String> symbols, LocalDate start, LocalDate end) {
        if (symbols.isEmpty()) {
            return List.of();
        }
        requireCredentials();
        List<DailyPrice> prices = new ArrayList<>();
        Set<String> seenPageTokens = new HashSet<>();
        String pageToken = null;
        do {
            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromUriString(properties.getAlpacaDataBaseUrl())
                    .path("/stocks/bars")
                    .queryParam("symbols", String.join(",", symbols))
                    .queryParam("timeframe", "1Day")
                    .queryParam("start", start)
                    .queryParam("end", end)
                    .queryParam("adjustment", "all")
                    .queryParam("feed", properties.getAlpacaFeed())
                    .queryParam("sort", "asc")
                    .queryParam("limit", PAGE_LIMIT);
            if (pageToken != null) {
                builder.queryParam("page_token", pageToken);
            }
            JsonNode root = readResponse(get(builder.build().encode().toUri()));
            prices.addAll(parseDailyBars(root));
            pageToken = nextPageToken(root);
            if (pageToken != null && !seenPageTokens.add(pageToken)) {
                throw new MarketDataProviderException(
                        "Alpaca returned a repeated daily-bars page token");
            }
        } while (pageToken != null);
        return prices;
    }

    @Override
    public List<IntradayBar> fetchIntradayBars(
            String symbol,
            String interval,
            LocalDateTime start,
            LocalDateTime end) {
        requireCredentials();
        List<IntradayBar> bars = new ArrayList<>();
        Set<String> seenPageTokens = new HashSet<>();
        String pageToken = null;
        do {
            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromUriString(properties.getAlpacaDataBaseUrl())
                    .pathSegment("stocks", symbol, "bars")
                    .queryParam("timeframe", alpacaTimeframe(interval))
                    .queryParam("start", start.atOffset(ZoneOffset.UTC))
                    .queryParam("end", end.atOffset(ZoneOffset.UTC))
                    .queryParam("adjustment", "all")
                    .queryParam("feed", properties.getAlpacaFeed())
                    .queryParam("sort", "asc")
                    .queryParam("limit", PAGE_LIMIT);
            if (pageToken != null) {
                builder.queryParam("page_token", pageToken);
            }
            JsonNode root = readResponse(get(builder.build().encode().toUri()));
            bars.addAll(parseIntradayBars(root, symbol, interval));
            pageToken = nextPageToken(root);
            if (pageToken != null && !seenPageTokens.add(pageToken)) {
                throw new MarketDataProviderException(
                        "Alpaca returned a repeated intraday-bars page token");
            }
        } while (pageToken != null);
        return bars;
    }

    @Override
    public boolean healthCheck() {
        return hasCredentials();
    }

    List<DailyPrice> parseDailyBars(String response) {
        return parseDailyBars(readResponse(response));
    }

    List<IntradayBar> parseIntradayBars(
            String response, String symbol, String interval) {
        return parseIntradayBars(readResponse(response), symbol, interval);
    }

    List<InstrumentSearchResult> parseAssets(String response) {
        JsonNode root = readResponse(response);
        if (!root.isArray()) {
            throw new MarketDataProviderException("Invalid Alpaca assets response");
        }
        List<InstrumentSearchResult> assets = new ArrayList<>();
        for (JsonNode item : root) {
            String symbol = item.path("symbol").asText();
            String name = item.path("name").asText();
            if (symbol.isBlank() || name.isBlank()) {
                continue;
            }
            assets.add(new InstrumentSearchResult(
                    symbol,
                    name,
                    item.path("exchange").asText(),
                    "USD",
                    item.path("class").asText("us_equity")));
        }
        return assets;
    }

    String nextPageToken(String response) {
        return nextPageToken(readResponse(response));
    }

    private List<InstrumentSearchResult> loadAssets() {
        requireCredentials();
        Instant now = Instant.now();
        if (now.isBefore(assetCacheExpiresAt)) {
            return cachedAssets;
        }
        synchronized (assetCacheLock) {
            now = Instant.now();
            if (now.isBefore(assetCacheExpiresAt)) {
                return cachedAssets;
            }
            URI uri = UriComponentsBuilder
                    .fromUriString(properties.getAlpacaApiBaseUrl())
                    .path("/assets")
                    .queryParam("status", "active")
                    .queryParam("asset_class", "us_equity")
                    .build()
                    .encode()
                    .toUri();
            cachedAssets = List.copyOf(parseAssets(get(uri)));
            assetCacheExpiresAt = now.plus(ASSET_CACHE_TTL);
            return cachedAssets;
        }
    }

    private List<DailyPrice> parseDailyBars(JsonNode root) {
        JsonNode barsBySymbol = root.path("bars");
        if (!barsBySymbol.isObject()) {
            throw responseError(root, "Invalid Alpaca daily-bars response");
        }
        List<DailyPrice> prices = new ArrayList<>();
        barsBySymbol.fields().forEachRemaining(entry -> {
            String symbol = entry.getKey();
            for (JsonNode bar : entry.getValue()) {
                OffsetDateTime timestamp = timestamp(bar);
                BigDecimal close = decimal(bar, "c");
                prices.add(new DailyPrice(
                        symbol,
                        timestamp.atZoneSameInstant(ZoneId.of(properties.getTimeZone()))
                                .toLocalDate(),
                        decimal(bar, "o"),
                        decimal(bar, "h"),
                        decimal(bar, "l"),
                        close,
                        close,
                        nullableLong(bar, "v"),
                        "USD",
                        name(),
                        timestamp.atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()));
            }
        });
        return prices;
    }

    private List<IntradayBar> parseIntradayBars(
            JsonNode root, String symbol, String interval) {
        JsonNode values = root.path("bars");
        if (!values.isArray()) {
            throw responseError(root, "Invalid Alpaca intraday-bars response");
        }
        String responseSymbol = root.path("symbol").asText(symbol);
        List<IntradayBar> bars = new ArrayList<>();
        for (JsonNode bar : values) {
            OffsetDateTime timestamp = timestamp(bar);
            bars.add(new IntradayBar(
                    responseSymbol,
                    interval,
                    timestamp.atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime(),
                    decimal(bar, "o"),
                    decimal(bar, "h"),
                    decimal(bar, "l"),
                    decimal(bar, "c"),
                    nullableLong(bar, "v"),
                    "USD",
                    name()));
        }
        return bars;
    }

    private JsonNode readResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            if (root == null) {
                throw new MarketDataProviderException("Alpaca returned an empty response");
            }
            if (root.hasNonNull("code") && root.hasNonNull("message")) {
                throw responseError(root, "Alpaca request failed");
            }
            return root;
        } catch (MarketDataProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new MarketDataProviderException(
                    "Invalid Alpaca response", exception);
        }
    }

    private String get(URI uri) {
        int maxAttempts = Math.max(1, properties.getMaxRetries() + 1);
        RestClientException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            rateLimiter.acquire();
            try {
                return client.get()
                        .uri(uri)
                        .header("APCA-API-KEY-ID", properties.getAlpacaApiKeyId())
                        .header(
                                "APCA-API-SECRET-KEY",
                                properties.getAlpacaApiSecretKey())
                        .retrieve()
                        .body(String.class);
            } catch (RestClientResponseException exception) {
                lastFailure = exception;
                if (!isRetryable(exception) || attempt == maxAttempts) {
                    throw httpFailure(exception);
                }
                waitBeforeRetry(exception, attempt);
            } catch (RestClientException exception) {
                lastFailure = exception;
                if (attempt == maxAttempts) {
                    break;
                }
                waitBeforeRetry(null, attempt);
            }
        }
        throw new MarketDataProviderException(
                "Alpaca request failed after retries", lastFailure);
    }

    private boolean isRetryable(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        return status == 429 || status >= 500;
    }

    private void waitBeforeRetry(
            RestClientResponseException exception, int attempt) {
        Duration delay = exception == null
                ? retryBackoff(attempt)
                : resetDelay(exception, attempt);
        log.warn(
                "Retrying Alpaca request in {} ms after attempt {}",
                delay.toMillis(),
                attempt);
        RequestRateLimiter.sleep(delay);
    }

    private Duration resetDelay(
            RestClientResponseException exception, int attempt) {
        if (exception.getStatusCode().value() == 429) {
            String reset = exception.getResponseHeaders() == null
                    ? null
                    : exception.getResponseHeaders().getFirst("X-RateLimit-Reset");
            if (reset != null) {
                try {
                    long seconds = Math.max(
                            1,
                            Long.parseLong(reset) - Instant.now().getEpochSecond() + 1);
                    return Duration.ofSeconds(seconds);
                } catch (NumberFormatException ignored) {
                    // Fall through to exponential backoff.
                }
            }
        }
        return retryBackoff(attempt);
    }

    private Duration retryBackoff(int attempt) {
        long baseMillis = Math.max(1, properties.getRetryBackoffMillis());
        long exponentialMillis = baseMillis * (1L << Math.min(attempt - 1, 10));
        long jitterMillis = ThreadLocalRandom.current().nextLong(baseMillis + 1);
        return Duration.ofMillis(exponentialMillis + jitterMillis);
    }

    private MarketDataProviderException httpFailure(
            RestClientResponseException exception) {
        String message = "Alpaca HTTP " + exception.getStatusCode().value();
        try {
            JsonNode root = objectMapper.readTree(exception.getResponseBodyAsString());
            String detail = root.path("message").asText();
            if (!detail.isBlank()) {
                message += ": " + detail;
            }
        } catch (Exception ignored) {
            // Keep the status-only message when the error body is not JSON.
        }
        return new MarketDataProviderException(message, exception);
    }

    private MarketDataProviderException responseError(
            JsonNode root, String fallback) {
        String message = root.path("message").asText();
        return new MarketDataProviderException(
                message.isBlank() ? fallback : "Alpaca error: " + message);
    }

    private String nextPageToken(JsonNode root) {
        JsonNode tokenNode = root.get("next_page_token");
        if (tokenNode == null || tokenNode.isNull()) {
            return null;
        }
        String token = tokenNode.asText();
        return token.isBlank() ? null : token;
    }

    private OffsetDateTime timestamp(JsonNode bar) {
        String value = bar.path("t").asText();
        if (value.isBlank()) {
            throw new MarketDataProviderException(
                    "Alpaca bar is missing its timestamp");
        }
        return OffsetDateTime.parse(value);
    }

    private BigDecimal decimal(JsonNode node, String field) {
        if (!node.hasNonNull(field)) {
            throw new MarketDataProviderException(
                    "Alpaca bar is missing field " + field);
        }
        return node.path(field).decimalValue();
    }

    private Long nullableLong(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.path(field).longValue() : null;
    }

    private String alpacaTimeframe(String interval) {
        String normalized = interval == null
                ? ""
                : interval.trim().toLowerCase(Locale.ROOT);
        if (normalized.matches("\\d+min")) {
            return normalized.substring(0, normalized.length() - 3) + "Min";
        }
        if (normalized.matches("\\d+h(our)?")) {
            return normalized.substring(0, normalized.indexOf('h')) + "Hour";
        }
        if ("1day".equals(normalized) || "1d".equals(normalized)) {
            return "1Day";
        }
        throw new MarketDataProviderException(
                "Unsupported Alpaca interval: " + interval);
    }

    private boolean hasCredentials() {
        return properties.getAlpacaApiKeyId() != null
                && !properties.getAlpacaApiKeyId().isBlank()
                && properties.getAlpacaApiSecretKey() != null
                && !properties.getAlpacaApiSecretKey().isBlank();
    }

    private void requireCredentials() {
        if (!hasCredentials()) {
            throw new MarketDataProviderException(
                    "ALPACA_API_KEY_ID and ALPACA_API_SECRET_KEY are required");
        }
    }
}
