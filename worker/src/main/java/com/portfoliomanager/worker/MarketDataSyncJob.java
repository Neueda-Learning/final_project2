package com.portfoliomanager.worker;

import com.portfoliomanager.worker.provider.DailyPrice;
import com.portfoliomanager.worker.provider.MarketDataProvider;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MarketDataSyncJob {

    private static final Logger log = LoggerFactory.getLogger(MarketDataSyncJob.class);
    private static final String LOCK_NAME = "portfolio_manager_market_sync";

    private final MarketDataProvider provider;
    private final JdbcTemplate jdbc;
    private final MarketDataProperties properties;
    private final Clock clock;

    public MarketDataSyncJob(
            MarketDataProvider provider,
            JdbcTemplate jdbc,
            MarketDataProperties properties) {
        this(provider, jdbc, properties, Clock.systemUTC());
    }

    MarketDataSyncJob(
            MarketDataProvider provider,
            JdbcTemplate jdbc,
            MarketDataProperties properties,
            Clock clock) {
        this.provider = provider;
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(
            cron = "${market-data.sync-cron}",
            zone = "${market-data.time-zone:America/New_York}")
    public void scheduledSync() {
        withGlobalLock(() -> {
            if (hasRunningSync()) {
                log.info("Scheduled market-data sync skipped because a run is active");
                return;
            }
            String runId = createRun("SCHEDULE");
            processRun(runId);
        });
    }

    @Scheduled(
            fixedDelayString = "${market-data.manual-poll-interval-ms:2000}",
            initialDelayString = "${market-data.manual-poll-interval-ms:2000}")
    public void processManualRequests() {
        withGlobalLock(() -> findPendingManualRun().ifPresent(this::processRun));
    }

    private void withGlobalLock(Runnable action) {
        Integer acquired =
                jdbc.queryForObject("SELECT GET_LOCK(?, 0)", Integer.class, LOCK_NAME);
        if (acquired == null || acquired != 1) {
            return;
        }
        try {
            action.run();
        } finally {
            jdbc.queryForObject("SELECT RELEASE_LOCK(?)", Integer.class, LOCK_NAME);
        }
    }

    private void processRun(String runId) {
        List<String> errors = new ArrayList<>();
        try {
            List<InstrumentTarget> targets = loadActiveTargets();
            jdbc.update(
                    "UPDATE market_data_sync_run SET requested_count = ? WHERE id = ?",
                    targets.size(),
                    runId);

            if (targets.isEmpty()) {
                completeRun(runId, 0, 0, "SUCCEEDED", null);
                return;
            }

            LocalDate end = LocalDate.now(clock.withZone(ZoneId.of(properties.getTimeZone())))
                    .plusDays(1);
            LocalDate start = end.minusDays(10);
            Map<String, InstrumentTarget> bySymbol = new HashMap<>();
            targets.forEach(target ->
                    bySymbol.put(normalizeSymbol(target.providerSymbol()), target));

            Set<String> successfulInstrumentIds = new HashSet<>();
            int batchSize = Math.max(1, properties.getBatchSize());
            for (int offset = 0; offset < targets.size(); offset += batchSize) {
                List<InstrumentTarget> batch =
                        targets.subList(offset, Math.min(offset + batchSize, targets.size()));
                List<String> symbols =
                        batch.stream().map(InstrumentTarget::providerSymbol).toList();
                try {
                    List<DailyPrice> prices = fetchWithRetry(symbols, start, end);
                    for (DailyPrice price : prices) {
                        InstrumentTarget target =
                                bySymbol.get(normalizeSymbol(price.symbol()));
                        if (target == null) {
                            errors.add("Provider returned unknown symbol " + price.symbol());
                            continue;
                        }
                        String validationError = validate(price, target, start, end);
                        if (validationError != null) {
                            errors.add(validationError);
                            continue;
                        }
                        upsertPrice(runId, target, price);
                        successfulInstrumentIds.add(target.instrumentId());
                    }
                    for (InstrumentTarget target : batch) {
                        if (!successfulInstrumentIds.contains(target.instrumentId())) {
                            errors.add("No valid price returned for " + target.providerSymbol());
                        }
                    }
                } catch (RuntimeException exception) {
                    errors.add("Batch " + String.join(",", symbols) + ": "
                            + rootMessage(exception));
                }
            }

            int successCount = successfulInstrumentIds.size();
            int failureCount = targets.size() - successCount;
            String status = successCount == targets.size()
                    ? "SUCCEEDED"
                    : successCount == 0 ? "FAILED" : "PARTIAL";
            completeRun(
                    runId,
                    successCount,
                    failureCount,
                    status,
                    errors.isEmpty() ? null : summarize(errors));
            log.info(
                    "Market-data sync {} completed with status {}, {} successful and {} failed",
                    runId,
                    status,
                    successCount,
                    failureCount);
        } catch (RuntimeException exception) {
            log.error("Market-data sync {} failed", runId, exception);
            failRun(runId, rootMessage(exception));
        }
    }

    private List<DailyPrice> fetchWithRetry(
            List<String> symbols, LocalDate start, LocalDate end) {
        RuntimeException lastFailure = null;
        int maxAttempts = Math.max(1, properties.getMaxRetries() + 1);
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return provider.fetchDailyCloses(symbols, start, end);
            } catch (RuntimeException exception) {
                lastFailure = exception;
                if (attempt < maxAttempts) {
                    pauseBeforeRetry(attempt);
                }
            }
        }
        throw lastFailure == null
                ? new IllegalStateException("Provider request failed")
                : lastFailure;
    }

    private void pauseBeforeRetry(int attempt) {
        try {
            Thread.sleep(Math.max(0, properties.getRetryBackoffMillis()) * attempt);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Market-data retry interrupted", exception);
        }
    }

    private List<InstrumentTarget> loadActiveTargets() {
        return jdbc.query(
                """
                SELECT DISTINCT i.id, COALESCE(i.provider_symbol, i.symbol) provider_symbol,
                                i.currency
                FROM portfolio_position p
                JOIN instrument i ON i.id = p.instrument_id
                WHERE p.quantity > 0 AND i.is_active = TRUE
                ORDER BY i.id
                """,
                (rs, rowNum) -> new InstrumentTarget(
                        rs.getString("id"),
                        rs.getString("provider_symbol"),
                        rs.getString("currency")));
    }

    private void upsertPrice(
            String runId, InstrumentTarget target, DailyPrice price) {
        jdbc.update(
                """
                INSERT INTO market_price (
                    instrument_id, sync_run_id, price_date, open_price, high_price,
                    low_price, close_price, adjusted_close, volume, currency, source,
                    source_timestamp, fetched_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6))
                ON DUPLICATE KEY UPDATE
                    sync_run_id = VALUES(sync_run_id),
                    open_price = VALUES(open_price),
                    high_price = VALUES(high_price),
                    low_price = VALUES(low_price),
                    close_price = VALUES(close_price),
                    adjusted_close = VALUES(adjusted_close),
                    volume = VALUES(volume),
                    currency = VALUES(currency),
                    source_timestamp = VALUES(source_timestamp),
                    fetched_at = CURRENT_TIMESTAMP(6)
                """,
                target.instrumentId(),
                runId,
                price.priceDate(),
                price.openPrice(),
                price.highPrice(),
                price.lowPrice(),
                price.closePrice(),
                price.adjustedClose(),
                price.volume(),
                price.currency(),
                price.source(),
                price.sourceTimestamp());
    }

    private String validate(
            DailyPrice price,
            InstrumentTarget target,
            LocalDate start,
            LocalDate end) {
        if (price.symbol() == null
                || !normalizeSymbol(price.symbol())
                        .equals(normalizeSymbol(target.providerSymbol()))) {
            return "Unexpected symbol for " + target.providerSymbol();
        }
        if (price.priceDate() == null
                || price.priceDate().isBefore(start)
                || !price.priceDate().isBefore(end)) {
            return "Invalid price date for " + target.providerSymbol();
        }
        if (!target.currency().equalsIgnoreCase(price.currency())) {
            return "Currency mismatch for " + target.providerSymbol();
        }
        if (!positive(price.closePrice())
                || !positiveOrNull(price.openPrice())
                || !positiveOrNull(price.highPrice())
                || !positiveOrNull(price.lowPrice())
                || !positiveOrNull(price.adjustedClose())) {
            return "Non-positive price for " + target.providerSymbol();
        }
        if (price.volume() != null && price.volume() < 0) {
            return "Negative volume for " + target.providerSymbol();
        }
        if (price.highPrice() != null
                && price.lowPrice() != null
                && price.highPrice().compareTo(price.lowPrice()) < 0) {
            return "High price below low price for " + target.providerSymbol();
        }
        return null;
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private boolean positiveOrNull(BigDecimal value) {
        return value == null || value.signum() > 0;
    }

    private boolean hasRunningSync() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM market_data_sync_run WHERE status = 'RUNNING'",
                Integer.class);
        return count != null && count > 0;
    }

    private java.util.Optional<String> findPendingManualRun() {
        return jdbc.query(
                        """
                        SELECT id
                        FROM market_data_sync_run
                        WHERE status = 'RUNNING' AND triggered_by = 'MANUAL'
                        ORDER BY started_at
                        LIMIT 1
                        """,
                        (rs, rowNum) -> rs.getString("id"))
                .stream()
                .findFirst();
    }

    private String createRun(String trigger) {
        String id = UUID.randomUUID().toString();
        jdbc.update(
                """
                INSERT INTO market_data_sync_run (
                    id, provider, status, requested_count, success_count,
                    failure_count, started_at, triggered_by
                ) VALUES (?, ?, 'RUNNING', 0, 0, 0, CURRENT_TIMESTAMP(6), ?)
                """,
                id,
                provider.name(),
                trigger);
        return id;
    }

    private void completeRun(
            String runId,
            int successCount,
            int failureCount,
            String status,
            String errorSummary) {
        jdbc.update(
                """
                UPDATE market_data_sync_run
                SET status = ?, success_count = ?, failure_count = ?,
                    completed_at = CURRENT_TIMESTAMP(6), error_summary = ?
                WHERE id = ?
                """,
                status,
                successCount,
                failureCount,
                errorSummary,
                runId);
    }

    private void failRun(String runId, String error) {
        Integer requested = jdbc.queryForObject(
                "SELECT requested_count FROM market_data_sync_run WHERE id = ?",
                Integer.class,
                runId);
        completeRun(
                runId,
                0,
                requested == null ? 0 : requested,
                "FAILED",
                summarize(List.of(error)));
    }

    private String summarize(List<String> errors) {
        String summary = String.join("; ", errors);
        return summary.length() <= 4000 ? summary : summary.substring(0, 4000);
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

    private String normalizeSymbol(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
    }

    private record InstrumentTarget(
            String instrumentId, String providerSymbol, String currency) {}
}
