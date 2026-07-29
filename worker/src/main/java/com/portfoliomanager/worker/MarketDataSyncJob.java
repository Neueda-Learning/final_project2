package com.portfoliomanager.worker;

import com.portfoliomanager.worker.provider.DailyPrice;
import com.portfoliomanager.worker.provider.IntradayBar;
import com.portfoliomanager.worker.provider.MarketDataProvider;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.ConnectionCallback;
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

    @Autowired
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

    @PostConstruct
    public void recoverAbandonedRuns() {
        int recovered = jdbc.update("""
                UPDATE market_data_sync_run
                SET status = 'FAILED', stage = 'COMPLETED',
                    completed_at = CURRENT_TIMESTAMP(6),
                    error_summary = 'Abandoned: worker restarted while sync was in progress'
                WHERE status = 'RUNNING'
                """);
        if (recovered > 0) {
            log.info("Recovered {} abandoned sync run(s) on worker startup", recovered);
        }
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
            fixedDelayString = "${market-data.manual-poll-interval-ms:15000}",
            initialDelayString = "${market-data.manual-poll-interval-ms:15000}")
    public void processManualRequests() {
        findPendingManualRun().ifPresent(runId ->
                withGlobalLock(() -> processRun(runId)));
    }

    private void withGlobalLock(Runnable action) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement acquire =
                    connection.prepareStatement("SELECT GET_LOCK(?, 0)")) {
                acquire.setString(1, LOCK_NAME);
                try (ResultSet result = acquire.executeQuery()) {
                    if (!result.next() || result.getInt(1) != 1) {
                        return null;
                    }
                }
            }

            try {
                action.run();
            } finally {
                try (PreparedStatement release =
                        connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
                    release.setString(1, LOCK_NAME);
                    release.executeQuery();
                }
            }
            return null;
        });
    }

    private void processRun(String runId) {
        List<String> errors = new ArrayList<>();
        try {
            List<InstrumentTarget> targets = loadActiveTargets();
            jdbc.update(
                    """
                    UPDATE market_data_sync_run
                    SET requested_count = ?, stage = 'FETCHING_MARKET_DATA'
                    WHERE id = ?
                    """,
                    targets.size(),
                    runId);

            if (targets.isEmpty()) {
                completeRun(runId, 0, 0, "SUCCEEDED", null);
                return;
            }

            LocalDate end = LocalDate.now(clock.withZone(ZoneId.of(properties.getTimeZone())))
                    .plusDays(1);
            LocalDate start = end.minusMonths(1);
            Map<String, InstrumentTarget> bySymbol = new HashMap<>();
            targets.forEach(target ->
                    bySymbol.put(normalizeSymbol(target.providerSymbol()), target));

            Set<String> validatedInstrumentIds = new HashSet<>();
            List<PriceWrite> priceWrites = new ArrayList<>();
            int batchSize = "twelve-data".equalsIgnoreCase(provider.name())
                    ? 1
                    : Math.max(1, properties.getBatchSize());
            int processedCount = 0;
            for (DailyBatchFetch fetch :
                    fetchDailyBatches(targets, batchSize, start, end)) {
                List<String> symbols = fetch.batch().stream()
                        .map(InstrumentTarget::providerSymbol)
                        .toList();
                if (fetch.error() != null) {
                    errors.add("Batch " + String.join(",", symbols) + ": "
                            + fetch.error());
                } else {
                    for (DailyPrice price : fetch.prices()) {
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
                        priceWrites.add(new PriceWrite(target, price));
                        validatedInstrumentIds.add(target.instrumentId());
                    }
                    for (InstrumentTarget target : fetch.batch()) {
                        if (!validatedInstrumentIds.contains(target.instrumentId())) {
                            errors.add("No valid price returned for " + target.providerSymbol());
                        }
                    }
                }
                processedCount += fetch.batch().size();
                updateRunProgress(
                        runId,
                        validatedInstrumentIds.size(),
                        processedCount - validatedInstrumentIds.size());
            }

            upsertPrices(runId, priceWrites);
            Set<String> successfulInstrumentIds = Set.copyOf(validatedInstrumentIds);

            // ── Intraday bars ────────────────────────────────────────────
            // Use UTC for both the request window and validation so that the
            // timestamps returned by the provider (which uses timezone=UTC) are
            // compared on the same axis.
            LocalDateTime intradayEnd = LocalDateTime.now(clock.withZone(ZoneOffset.UTC));
            LocalDateTime intradayStart =
                    intradayEnd.minusDays(properties.getIntradayLookbackDays());
            for (IntradayFetch fetch :
                    fetchIntradayBatches(targets, intradayStart, intradayEnd)) {
                InstrumentTarget target = fetch.target();
                // Fetch intraday for every active target regardless of whether
                // its daily price succeeded – the two data types are independent.
                if (fetch.error() == null) {
                    List<IntradayBar> bars = fetch.bars();
                    List<IntradayBar> validBars = bars.stream()
                            .filter(bar -> validateIntraday(
                                    bar, target, intradayStart, intradayEnd) == null)
                            .toList();
                    if (!validBars.isEmpty()) {
                        upsertIntradayBars(target, validBars);
                        log.info(
                                "Upserted {} intraday bars for {}",
                                validBars.size(),
                                target.providerSymbol());
                    } else if (!bars.isEmpty()) {
                        // All bars returned by the provider failed validation –
                        // log the first failure reason to aid debugging.
                        String sampleError =
                                validateIntraday(bars.get(0), target, intradayStart, intradayEnd);
                        log.warn(
                                "Fetched {} bars for {} but all failed validation (e.g. {})",
                                bars.size(),
                                target.providerSymbol(),
                                sampleError);
                    } else {
                        log.warn("Provider returned 0 intraday bars for {}", target.providerSymbol());
                    }
                } else {
                    log.warn(
                            "Intraday fetch failed for {}: {}",
                            target.providerSymbol(),
                            fetch.error());
                    errors.add("Intraday " + target.providerSymbol()
                            + ": " + fetch.error());
                }
            }

            int successCount = successfulInstrumentIds.size();
            int failureCount = targets.size() - successCount;
            String status = successCount == targets.size()
                    ? "SUCCEEDED"
                    : successCount == 0 ? "FAILED" : "PARTIAL";
            updateRunStage(runId, "REFRESHING_CURRENT_VALUATIONS");
            refreshValuationSnapshots(successfulInstrumentIds, errors);
            updateRunStage(runId, "REBUILDING_HISTORICAL_VALUATIONS");
            rebuildHistoricalValuationSnapshots(
                    successfulInstrumentIds, start, end, errors);
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

    private List<DailyBatchFetch> fetchDailyBatches(
            List<InstrumentTarget> targets,
            int batchSize,
            LocalDate start,
            LocalDate end) {
        List<Callable<DailyBatchFetch>> tasks = new ArrayList<>();
        for (int offset = 0; offset < targets.size(); offset += batchSize) {
            List<InstrumentTarget> batch = List.copyOf(
                    targets.subList(offset, Math.min(offset + batchSize, targets.size())));
            tasks.add(() -> {
                List<String> symbols =
                        batch.stream().map(InstrumentTarget::providerSymbol).toList();
                try {
                    return new DailyBatchFetch(
                            batch, fetchWithRetry(symbols, start, end), null);
                } catch (RuntimeException exception) {
                    return new DailyBatchFetch(
                            batch, List.of(), rootMessage(exception));
                }
            });
        }
        return executeProviderTasks(tasks);
    }

    private List<IntradayFetch> fetchIntradayBatches(
            List<InstrumentTarget> targets,
            LocalDateTime start,
            LocalDateTime end) {
        List<Callable<IntradayFetch>> tasks = targets.stream()
                .<Callable<IntradayFetch>>map(target -> () -> {
                    try {
                        return new IntradayFetch(
                                target,
                                fetchIntradayWithRetry(target, start, end),
                                null);
                    } catch (RuntimeException exception) {
                        return new IntradayFetch(
                                target, List.of(), rootMessage(exception));
                    }
                })
                .toList();
        return executeProviderTasks(tasks);
    }

    private <T> List<T> executeProviderTasks(List<Callable<T>> tasks) {
        if (tasks.isEmpty()) {
            return List.of();
        }
        int concurrency = "twelve-data".equalsIgnoreCase(provider.name())
                ? 1
                : Math.max(1, properties.getRequestConcurrency());
        if (concurrency == 1 || tasks.size() == 1) {
            List<T> results = new ArrayList<>(tasks.size());
            for (Callable<T> task : tasks) {
                try {
                    results.add(task.call());
                } catch (RuntimeException exception) {
                    throw exception;
                } catch (Exception exception) {
                    throw new IllegalStateException(
                            "Market-data task failed", exception);
                }
            }
            return results;
        }

        ExecutorService executor =
                Executors.newFixedThreadPool(Math.min(concurrency, tasks.size()));
        try {
            List<Future<T>> futures = executor.invokeAll(tasks);
            List<T> results = new ArrayList<>(futures.size());
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Market-data concurrency was interrupted", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException(
                    "Market-data concurrent task failed", exception.getCause());
        } finally {
            executor.shutdownNow();
        }
    }

    private List<IntradayBar> fetchIntradayWithRetry(
            InstrumentTarget target,
            LocalDateTime start,
            LocalDateTime end) {
        RuntimeException lastFailure = null;
        int maxAttempts = Math.max(1, properties.getMaxRetries() + 1);
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return provider.fetchIntradayBars(
                        target.providerSymbol(),
                        properties.getIntradayInterval(),
                        start,
                        end);
            } catch (RuntimeException exception) {
                lastFailure = exception;
                if (attempt < maxAttempts) {
                    pauseBeforeRetry(attempt);
                }
            }
        }
        throw lastFailure == null
                ? new IllegalStateException("Intraday provider request failed")
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

    private void refreshValuationSnapshots(
            Set<String> successfulInstrumentIds,
            List<String> errors) {
        if (successfulInstrumentIds.isEmpty()) {
            return;
        }

        List<SnapshotWrite> snapshots = new ArrayList<>();
        for (String portfolioId : loadAffectedPortfolioIds(successfulInstrumentIds)) {
            try {
                SnapshotSummary summary = loadSnapshotSummary(portfolioId);
                if (summary == null || summary.valuationDate() == null) {
                    continue;
                }
                snapshots.add(new SnapshotWrite(portfolioId, summary));
            } catch (RuntimeException exception) {
                errors.add(
                        "Snapshot refresh failed for portfolio "
                                + portfolioId
                                + ": "
                                + rootMessage(exception));
            }
        }
        try {
            upsertSnapshots(snapshots);
        } catch (RuntimeException exception) {
            errors.add("Snapshot batch refresh failed: " + rootMessage(exception));
        }
    }

    private void rebuildHistoricalValuationSnapshots(
            Set<String> successfulInstrumentIds,
            LocalDate start,
            LocalDate end,
            List<String> errors) {
        if (successfulInstrumentIds.isEmpty()) {
            return;
        }

        List<SnapshotWrite> snapshots = new ArrayList<>();
        for (String portfolioId : loadAffectedPortfolioIds(successfulInstrumentIds)) {
            try {
                snapshots.addAll(
                        buildHistoricalValuationSnapshots(portfolioId, start, end));
            } catch (RuntimeException exception) {
                errors.add(
                        "Historical snapshot rebuild failed for portfolio "
                                + portfolioId
                                + ": "
                                + rootMessage(exception));
            }
        }
        try {
            upsertSnapshots(snapshots);
        } catch (RuntimeException exception) {
            errors.add("Historical snapshot batch upsert failed: " + rootMessage(exception));
        }
    }

    private List<SnapshotWrite> buildHistoricalValuationSnapshots(
            String portfolioId, LocalDate start, LocalDate end) {
        List<TradeEvent> trades = loadTrades(portfolioId, end);
        List<DailyClose> closes = loadDailyCloses(portfolioId, start, end);
        if (trades.isEmpty() || closes.isEmpty()) {
            log.warn(
                    "Historical snapshot rebuild skipped for portfolio {}: {} trades, {} closes",
                    portfolioId,
                    trades.size(),
                    closes.size());
            return List.of();
        }

        Map<LocalDate, Map<String, BigDecimal>> closesByDate = new java.util.TreeMap<>();
        for (DailyClose close : closes) {
            closesByDate
                    .computeIfAbsent(close.priceDate(), ignored -> new HashMap<>())
                    .put(close.instrumentId(), close.closePrice());
        }

        Map<String, PositionLedger> positions = new HashMap<>();
        List<SnapshotWrite> snapshots = new ArrayList<>();
        int tradeIndex = 0;
        for (Map.Entry<LocalDate, Map<String, BigDecimal>> day : closesByDate.entrySet()) {
            LocalDate valuationDate = day.getKey();
            while (tradeIndex < trades.size()
                    && !trades.get(tradeIndex).executedAt().toLocalDate().isAfter(valuationDate)) {
                applyTrade(positions, trades.get(tradeIndex));
                tradeIndex++;
            }

            SnapshotSummary summary =
                    summarizeHistoricalDay(valuationDate, positions, day.getValue());
            if (summary.pricedPositionCount() > 0) {
                snapshots.add(new SnapshotWrite(portfolioId, summary));
            }
        }
        log.info(
                "Rebuilt {} historical snapshots for portfolio {} from {} daily closes",
                snapshots.size(),
                portfolioId,
                closes.size());
        return snapshots;
    }

    private List<TradeEvent> loadTrades(String portfolioId, LocalDate end) {
        return jdbc.query(
                """
                SELECT instrument_id, side, quantity, unit_price, fee_amount, executed_at
                FROM trade_transaction
                WHERE portfolio_id = ?
                  AND executed_at < ?
                ORDER BY executed_at, created_at, id
                """,
                (rs, rowNum) -> new TradeEvent(
                        rs.getString("instrument_id"),
                        rs.getString("side"),
                        rs.getBigDecimal("quantity"),
                        rs.getBigDecimal("unit_price"),
                        rs.getBigDecimal("fee_amount"),
                        rs.getTimestamp("executed_at").toLocalDateTime()),
                portfolioId,
                end.atStartOfDay());
    }

    private List<DailyClose> loadDailyCloses(
            String portfolioId, LocalDate start, LocalDate end) {
        return jdbc.query(
                """
                SELECT mp.instrument_id, mp.price_date, mp.close_price
                FROM market_price mp
                WHERE mp.price_date >= ?
                  AND mp.price_date < ?
                  AND NOT EXISTS (
                      SELECT 1
                      FROM market_price newer
                      WHERE newer.instrument_id = mp.instrument_id
                        AND newer.price_date = mp.price_date
                        AND (
                            newer.fetched_at > mp.fetched_at
                            OR (
                                newer.fetched_at = mp.fetched_at
                                AND newer.id > mp.id
                            )
                        )
                  )
                  AND EXISTS (
                      SELECT 1
                      FROM trade_transaction t
                      WHERE t.portfolio_id = ?
                        AND t.instrument_id = mp.instrument_id
                  )
                ORDER BY mp.price_date, mp.instrument_id
                """,
                (rs, rowNum) -> new DailyClose(
                        rs.getString("instrument_id"),
                        rs.getDate("price_date").toLocalDate(),
                        rs.getBigDecimal("close_price")),
                start,
                end,
                portfolioId);
    }

    private void applyTrade(
            Map<String, PositionLedger> positions, TradeEvent trade) {
        PositionLedger position =
                positions.computeIfAbsent(trade.instrumentId(), ignored -> new PositionLedger());
        if ("BUY".equals(trade.side())) {
            BigDecimal newQuantity = position.quantity.add(trade.quantity());
            BigDecimal newCostBasis = position.quantity
                    .multiply(position.averageCost)
                    .add(trade.quantity().multiply(trade.unitPrice()))
                    .add(trade.feeAmount());
            position.quantity = newQuantity;
            position.averageCost = newCostBasis.divide(newQuantity, 8, RoundingMode.HALF_UP);
            return;
        }

        position.quantity = position.quantity.subtract(trade.quantity());
        if (position.quantity.signum() <= 0) {
            position.quantity = BigDecimal.ZERO;
            position.averageCost = BigDecimal.ZERO;
        }
    }

    private SnapshotSummary summarizeHistoricalDay(
            LocalDate valuationDate,
            Map<String, PositionLedger> positions,
            Map<String, BigDecimal> closes) {
        BigDecimal pricedMarketValue = BigDecimal.ZERO;
        BigDecimal totalCostBasis = BigDecimal.ZERO;
        BigDecimal pricedCostBasis = BigDecimal.ZERO;
        int pricedPositionCount = 0;
        int unpricedPositionCount = 0;

        for (Map.Entry<String, PositionLedger> entry : positions.entrySet()) {
            PositionLedger position = entry.getValue();
            if (position.quantity.signum() <= 0) {
                continue;
            }

            BigDecimal costBasis = position.quantity.multiply(position.averageCost);
            totalCostBasis = totalCostBasis.add(costBasis);
            BigDecimal close = closes.get(entry.getKey());
            if (close == null) {
                unpricedPositionCount++;
                continue;
            }

            pricedPositionCount++;
            pricedCostBasis = pricedCostBasis.add(costBasis);
            pricedMarketValue =
                    pricedMarketValue.add(position.quantity.multiply(close));
        }

        return new SnapshotSummary(
                valuationDate,
                pricedMarketValue.setScale(8, RoundingMode.HALF_UP),
                totalCostBasis.setScale(8, RoundingMode.HALF_UP),
                pricedCostBasis.setScale(8, RoundingMode.HALF_UP),
                pricedMarketValue
                        .subtract(pricedCostBasis)
                        .setScale(8, RoundingMode.HALF_UP),
                pricedPositionCount,
                unpricedPositionCount);
    }

    private List<String> loadAffectedPortfolioIds(Set<String> instrumentIds) {
        String placeholders = String.join(",", java.util.Collections.nCopies(instrumentIds.size(), "?"));
        return jdbc.query(
                """
                SELECT DISTINCT p.portfolio_id
                FROM portfolio_position p
                WHERE p.quantity > 0
                  AND p.instrument_id IN (
                """ + placeholders + ") ORDER BY p.portfolio_id",
                (rs, rowNum) -> rs.getString("portfolio_id"),
                instrumentIds.toArray());
    }

    private SnapshotSummary loadSnapshotSummary(String portfolioId) {
        return jdbc.query(
                        """
                        SELECT newest_price_date, priced_market_value, total_cost_basis,
                               priced_cost_basis, unrealized_pnl,
                               priced_position_count, unpriced_position_count
                        FROM portfolio_summary
                        WHERE portfolio_id = ?
                        """,
                        (rs, rowNum) -> new SnapshotSummary(
                                rs.getDate("newest_price_date") == null
                                        ? null
                                        : rs.getDate("newest_price_date").toLocalDate(),
                                rs.getBigDecimal("priced_market_value"),
                                rs.getBigDecimal("total_cost_basis"),
                                rs.getBigDecimal("priced_cost_basis"),
                                rs.getBigDecimal("unrealized_pnl"),
                                rs.getInt("priced_position_count"),
                                rs.getInt("unpriced_position_count")),
                        portfolioId)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private void upsertSnapshots(List<SnapshotWrite> snapshots) {
        final int chunkSize = 200;
        for (int offset = 0; offset < snapshots.size(); offset += chunkSize) {
            List<SnapshotWrite> chunk =
                    snapshots.subList(offset, Math.min(offset + chunkSize, snapshots.size()));
            String placeholders = String.join(
                    ",",
                    java.util.Collections.nCopies(
                            chunk.size(), "(?, ?, ?, ?, ?, ?, ?, ?)"));
            String sql = """
                INSERT INTO portfolio_valuation_snapshot (
                    portfolio_id, valuation_date, priced_market_value,
                    total_cost_basis, priced_cost_basis, unrealized_pnl,
                    priced_position_count, unpriced_position_count
                ) VALUES
                """ + placeholders + """
                ON DUPLICATE KEY UPDATE
                    priced_market_value = VALUES(priced_market_value),
                    total_cost_basis = VALUES(total_cost_basis),
                    priced_cost_basis = VALUES(priced_cost_basis),
                    unrealized_pnl = VALUES(unrealized_pnl),
                    priced_position_count = VALUES(priced_position_count),
                    unpriced_position_count = VALUES(unpriced_position_count),
                    calculated_at = CURRENT_TIMESTAMP(6)
                """;
            List<Object> parameters = new ArrayList<>(chunk.size() * 8);
            for (SnapshotWrite snapshot : chunk) {
                parameters.add(snapshot.portfolioId());
                parameters.add(snapshot.summary().valuationDate());
                parameters.add(snapshot.summary().pricedMarketValue());
                parameters.add(snapshot.summary().totalCostBasis());
                parameters.add(snapshot.summary().pricedCostBasis());
                parameters.add(snapshot.summary().unrealizedPnl());
                parameters.add(snapshot.summary().pricedPositionCount());
                parameters.add(snapshot.summary().unpricedPositionCount());
            }
            jdbc.update(sql, parameters.toArray());
        }
    }

    private void upsertPrices(String runId, List<PriceWrite> prices) {
        final int chunkSize = 200;
        for (int offset = 0; offset < prices.size(); offset += chunkSize) {
            List<PriceWrite> chunk =
                    prices.subList(offset, Math.min(offset + chunkSize, prices.size()));
            String placeholders = String.join(
                    ",",
                    java.util.Collections.nCopies(
                            chunk.size(),
                            "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6))"));
            String sql = """
                INSERT INTO market_price (
                    instrument_id, sync_run_id, price_date, open_price, high_price,
                    low_price, close_price, adjusted_close, volume, currency, source,
                    source_timestamp, fetched_at
                ) VALUES
                """ + placeholders + """
                ON DUPLICATE KEY UPDATE
                    sync_run_id = VALUES(sync_run_id),
                    open_price = VALUES(open_price),
                    high_price = VALUES(high_price),
                    low_price = VALUES(low_price),
                    close_price = VALUES(close_price),
                    adjusted_close = VALUES(adjusted_close),
                    volume = VALUES(volume),
                    currency = VALUES(currency),
                    source = VALUES(source),
                    source_timestamp = VALUES(source_timestamp),
                    fetched_at = CURRENT_TIMESTAMP(6)
                """;
            List<Object> parameters = new ArrayList<>(chunk.size() * 12);
            for (PriceWrite write : chunk) {
                DailyPrice price = write.price();
                parameters.add(write.target().instrumentId());
                parameters.add(runId);
                parameters.add(price.priceDate());
                parameters.add(price.openPrice());
                parameters.add(price.highPrice());
                parameters.add(price.lowPrice());
                parameters.add(price.closePrice());
                parameters.add(price.adjustedClose());
                parameters.add(price.volume());
                parameters.add(price.currency());
                parameters.add(price.source());
                parameters.add(price.sourceTimestamp());
            }
            jdbc.update(sql, parameters.toArray());
        }
    }

    private void upsertIntradayBars(
            InstrumentTarget target, List<IntradayBar> bars) {
        final int chunkSize = 200;
        for (int offset = 0; offset < bars.size(); offset += chunkSize) {
            List<IntradayBar> chunk =
                    bars.subList(offset, Math.min(offset + chunkSize, bars.size()));
            String placeholders = String.join(
                    ",",
                    java.util.Collections.nCopies(
                            chunk.size(), "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6))"));
            String sql = """
                INSERT INTO market_intraday_bar (
                    instrument_id, interval_code, bar_timestamp,
                    open_price, high_price, low_price, close_price,
                    volume, currency, source, fetched_at
                ) VALUES
                """ + placeholders + """
                ON DUPLICATE KEY UPDATE
                    open_price = VALUES(open_price),
                    high_price = VALUES(high_price),
                    low_price = VALUES(low_price),
                    close_price = VALUES(close_price),
                    volume = VALUES(volume),
                    currency = VALUES(currency),
                    source = VALUES(source),
                    fetched_at = CURRENT_TIMESTAMP(6)
                """;
            List<Object> parameters = new ArrayList<>(chunk.size() * 10);
            for (IntradayBar bar : chunk) {
                parameters.add(target.instrumentId());
                parameters.add(bar.interval());
                parameters.add(bar.timestamp());
                parameters.add(bar.openPrice());
                parameters.add(bar.highPrice());
                parameters.add(bar.lowPrice());
                parameters.add(bar.closePrice());
                parameters.add(bar.volume());
                parameters.add(bar.currency());
                parameters.add(bar.source());
            }
            jdbc.update(sql, parameters.toArray());
        }
    }

    private String validateIntraday(
            IntradayBar bar,
            InstrumentTarget target,
            LocalDateTime start,
            LocalDateTime end) {
        if (!normalizeSymbol(bar.symbol())
                .equals(normalizeSymbol(target.providerSymbol()))) {
            return "Unexpected intraday symbol for " + target.providerSymbol();
        }
        if (!properties.getIntradayInterval().equals(bar.interval())) {
            return "Unexpected interval for " + target.providerSymbol();
        }
        if (bar.timestamp() == null
                || bar.timestamp().isBefore(start)
                || bar.timestamp().isAfter(end)) {
            return "Invalid intraday timestamp for " + target.providerSymbol();
        }
        if (!target.currency().equalsIgnoreCase(bar.currency())) {
            return "Currency mismatch for " + target.providerSymbol();
        }
        if (!positive(bar.openPrice())
                || !positive(bar.highPrice())
                || !positive(bar.lowPrice())
                || !positive(bar.closePrice())
                || bar.highPrice().compareTo(bar.lowPrice()) < 0) {
            return "Invalid intraday OHLC for " + target.providerSymbol();
        }
        if (bar.volume() != null && bar.volume() < 0) {
            return "Negative intraday volume for " + target.providerSymbol();
        }
        return null;
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
                SET status = ?, stage = 'COMPLETED',
                    success_count = ?, failure_count = ?,
                    completed_at = CURRENT_TIMESTAMP(6), error_summary = ?
                WHERE id = ?
                """,
                status,
                successCount,
                failureCount,
                errorSummary,
                runId);
    }

    private void updateRunProgress(String runId, int successCount, int failureCount) {
        jdbc.update(
                """
                UPDATE market_data_sync_run
                SET success_count = ?, failure_count = ?
                WHERE id = ?
                """,
                successCount,
                failureCount,
                runId);
    }

    private void updateRunStage(String runId, String stage) {
        jdbc.update(
                "UPDATE market_data_sync_run SET stage = ? WHERE id = ?",
                stage,
                runId);
    }

    private void failRun(String runId, String error) {
        jdbc.update(
                """
                UPDATE market_data_sync_run
                SET status = 'FAILED', stage = 'COMPLETED',
                    failure_count = requested_count - success_count,
                    completed_at = CURRENT_TIMESTAMP(6), error_summary = ?
                WHERE id = ?
                """,
                summarize(List.of(error)),
                runId);
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

    private record DailyBatchFetch(
            List<InstrumentTarget> batch, List<DailyPrice> prices, String error) {}

    private record IntradayFetch(
            InstrumentTarget target, List<IntradayBar> bars, String error) {}

    private record PriceWrite(InstrumentTarget target, DailyPrice price) {}

    private record SnapshotWrite(String portfolioId, SnapshotSummary summary) {}

    private record TradeEvent(
            String instrumentId,
            String side,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal feeAmount,
            LocalDateTime executedAt) {}

    private record DailyClose(
            String instrumentId, LocalDate priceDate, BigDecimal closePrice) {}

    private static final class DailyAccumulator {
        private final InstrumentTarget target;
        private final LocalDate date;
        private IntradayBar first;
        private IntradayBar last;
        private BigDecimal high;
        private BigDecimal low;
        private long volume;
        private boolean hasVolume;

        private DailyAccumulator(InstrumentTarget target, IntradayBar initial) {
            this.target = target;
            this.date = initial.timestamp().toLocalDate();
            this.first = initial;
            this.last = initial;
            this.high = initial.highPrice();
            this.low = initial.lowPrice();
        }

        private void add(IntradayBar bar) {
            if (bar.timestamp().isBefore(first.timestamp())) {
                first = bar;
            }
            if (bar.timestamp().isAfter(last.timestamp())) {
                last = bar;
            }
            high = high.max(bar.highPrice());
            low = low.min(bar.lowPrice());
            if (bar.volume() != null) {
                volume += bar.volume();
                hasVolume = true;
            }
        }

        private InstrumentTarget target() {
            return target;
        }

        private DailyPrice toDailyPrice() {
            return new DailyPrice(
                    target.providerSymbol(),
                    date,
                    first.openPrice(),
                    high,
                    low,
                    last.closePrice(),
                    last.closePrice(),
                    hasVolume ? volume : null,
                    last.currency(),
                    last.source(),
                    last.timestamp());
        }
    }

    private static final class PositionLedger {
        private BigDecimal quantity = BigDecimal.ZERO;
        private BigDecimal averageCost = BigDecimal.ZERO;
    }

    private record SnapshotSummary(
            LocalDate valuationDate,
            BigDecimal pricedMarketValue,
            BigDecimal totalCostBasis,
            BigDecimal pricedCostBasis,
            BigDecimal unrealizedPnl,
            int pricedPositionCount,
            int unpricedPositionCount) {}
}
