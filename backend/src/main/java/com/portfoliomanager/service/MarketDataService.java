package com.portfoliomanager.service;

import com.portfoliomanager.api.ApiModels.MarketPriceResponse;
import com.portfoliomanager.api.ApiModels.MarketBarPageResponse;
import com.portfoliomanager.api.ApiModels.MarketBarResponse;
import com.portfoliomanager.api.ApiModels.SyncRunResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarketDataService {

    private static final String LOCK_NAME = "portfolio_manager_market_sync";
    private static final Set<String> INTRADAY_INTERVALS =
            Set.of("1min", "5min", "15min", "30min");

    private final JdbcTemplate jdbc;
    private final MarketCalendarService calendar;
    private final String provider;

    public MarketDataService(
            JdbcTemplate jdbc,
            MarketCalendarService calendar,
            @Value("${market-data.provider:twelve-data}") String provider) {
        this.jdbc = jdbc;
        this.calendar = calendar;
        this.provider = provider;
    }

    @Transactional
    public SyncRunResponse requestManualSync(boolean force) {
        Optional<SyncRunResponse> running = currentRunningSync();
        if (running.isPresent() && !force) {
            return running.get();
        }

        Integer acquired =
                jdbc.queryForObject("SELECT GET_LOCK(?, 0)", Integer.class, LOCK_NAME);
        if (acquired == null || acquired != 1) {
            return currentRunningSync().orElseThrow(
                    () -> new MarketDataUnavailableException(
                            "Market-data synchronization is unavailable. Please try again later."));
        }

        try {
            if (force) {
                // Abandon any stuck running sync so a fresh one can start
                jdbc.update("""
                        UPDATE market_data_sync_run
                        SET status = 'FAILED', stage = 'COMPLETED',
                            completed_at = CURRENT_TIMESTAMP(6),
                            error_summary = 'Abandoned: force-resync requested'
                        WHERE status = 'RUNNING'
                        """);
            } else {
                running = currentRunningSync();
                if (running.isPresent()) {
                    return running.get();
                }
            }

            String id = UUID.randomUUID().toString();
            jdbc.update(
                    """
                    INSERT INTO market_data_sync_run (
                        id, provider, status, requested_count, success_count,
                        failure_count, started_at, triggered_by
                    ) VALUES (?, ?, 'RUNNING', 0, 0, 0, CURRENT_TIMESTAMP(6), 'MANUAL')
                    """,
                    id,
                    provider);
            return findSyncRun(id).orElseThrow();
        } finally {
            jdbc.queryForObject("SELECT RELEASE_LOCK(?)", Integer.class, LOCK_NAME);
        }
    }

    public Optional<SyncRunResponse> latestSyncRun() {
        return querySyncRuns(
                        """
                        SELECT id, provider, status, stage, requested_count, success_count,
                               failure_count, started_at, completed_at, triggered_by,
                               error_summary
                        FROM market_data_sync_run
                        ORDER BY started_at DESC
                        LIMIT 1
                        """)
                .stream()
                .findFirst();
    }

    public MarketPriceResponse latestPrice(String instrumentId) {
        requireInstrument(instrumentId);

        return jdbc.query(
                        """
                        SELECT i.id AS instrument_id, i.symbol, lmp.price_date,
                               lmp.close_price, lmp.adjusted_close, lmp.currency,
                               lmp.source, lmp.source_timestamp, lmp.fetched_at
                        FROM instrument i
                        JOIN latest_market_price lmp ON lmp.instrument_id = i.id
                        WHERE i.id = ?
                        """,
                        (rs, rowNum) -> new MarketPriceResponse(
                                rs.getString("instrument_id"),
                                rs.getString("symbol"),
                                rs.getDate("price_date").toLocalDate(),
                                rs.getBigDecimal("close_price"),
                                rs.getBigDecimal("adjusted_close"),
                                rs.getString("currency"),
                                rs.getString("source"),
                                toLocalDateTime(rs, "source_timestamp"),
                                toLocalDateTime(rs, "fetched_at"),
                                calendar.status(rs.getDate("price_date").toLocalDate())),
                        instrumentId)
                .stream()
                .findFirst()
                .orElseThrow(() ->
                        new ResourceNotFoundException("No market data is available for instrument: " + instrumentId));
    }

    public List<MarketPriceResponse> tradablePrices(String instrumentId, int limit) {
        requireInstrument(instrumentId);
        return jdbc.query(
                """
                SELECT i.id AS instrument_id, i.symbol, mp.price_date,
                       mp.close_price, mp.adjusted_close, mp.currency,
                       mp.source, mp.source_timestamp, mp.fetched_at
                FROM instrument i
                JOIN market_price mp ON mp.instrument_id = i.id
                WHERE i.id = ?
                  AND mp.close_price > 0
                  AND NOT EXISTS (
                      SELECT 1
                      FROM market_price newer
                      WHERE newer.instrument_id = mp.instrument_id
                        AND newer.price_date = mp.price_date
                        AND newer.fetched_at > mp.fetched_at
                  )
                ORDER BY mp.price_date DESC, mp.fetched_at DESC
                LIMIT ?
                """,
                this::mapMarketPrice,
                instrumentId,
                limit);
    }

    @Cacheable(
            cacheNames = "marketBars",
            key = "#instrumentId + '|' + #interval + '|' + #from + '|' + #to"
                    + " + '|' + #page + '|' + #pageSize",
            sync = true)
    public MarketBarPageResponse bars(
            String instrumentId,
            String interval,
            LocalDateTime from,
            LocalDateTime to,
            int page,
            int pageSize) {
        requireInstrument(instrumentId);
        if (!INTRADAY_INTERVALS.contains(interval)) {
            throw new IllegalArgumentException(
                    "interval must be one of 1min, 5min, 15min, or 30min");
        }
        LocalDateTime effectiveTo =
                to == null ? LocalDateTime.now(ZoneOffset.UTC) : to;
        LocalDateTime effectiveFrom =
                from == null ? effectiveTo.minusDays(1) : from;
        if (!effectiveFrom.isBefore(effectiveTo)) {
            throw new InvalidDateRangeException("from must be earlier than to");
        }
        if (effectiveFrom.isBefore(effectiveTo.minusDays(31))) {
            throw new InvalidDateRangeException(
                    "Intraday queries are limited to 31 days");
        }

        Long total = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM market_intraday_bar
                WHERE instrument_id = ?
                  AND interval_code = ?
                  AND bar_timestamp >= ?
                  AND bar_timestamp < ?
                """,
                Long.class,
                instrumentId,
                interval,
                effectiveFrom,
                effectiveTo);
        int offset = (page - 1) * pageSize;
        List<MarketBarResponse> items = jdbc.query(
                """
                SELECT b.instrument_id, i.symbol, b.interval_code,
                       b.bar_timestamp, b.open_price, b.high_price,
                       b.low_price, b.close_price, b.volume,
                       b.currency, b.source
                FROM market_intraday_bar b
                JOIN instrument i ON i.id = b.instrument_id
                WHERE b.instrument_id = ?
                  AND b.interval_code = ?
                  AND b.bar_timestamp >= ?
                  AND b.bar_timestamp < ?
                ORDER BY b.bar_timestamp DESC
                LIMIT ? OFFSET ?
                """,
                (rs, rowNum) -> new MarketBarResponse(
                        rs.getString("instrument_id"),
                        rs.getString("symbol"),
                        rs.getString("interval_code"),
                        rs.getTimestamp("bar_timestamp").toLocalDateTime(),
                        rs.getBigDecimal("open_price"),
                        rs.getBigDecimal("high_price"),
                        rs.getBigDecimal("low_price"),
                        rs.getBigDecimal("close_price"),
                        rs.getObject("volume", Long.class),
                        rs.getString("currency"),
                        rs.getString("source")),
                instrumentId,
                interval,
                effectiveFrom,
                effectiveTo,
                pageSize,
                offset);
        long count = total == null ? 0 : total;
        return new MarketBarPageResponse(
                items, page, pageSize, count, (long) offset + items.size() < count);
    }

    public MarketPriceResponse tradablePrice(String instrumentId, LocalDate priceDate) {
        requireInstrument(instrumentId);
        return jdbc.query(
                        """
                        SELECT i.id AS instrument_id, i.symbol, mp.price_date,
                               mp.close_price, mp.adjusted_close, mp.currency,
                               mp.source, mp.source_timestamp, mp.fetched_at
                        FROM instrument i
                        JOIN market_price mp ON mp.instrument_id = i.id
                        WHERE i.id = ?
                          AND mp.price_date = ?
                          AND mp.close_price > 0
                        ORDER BY mp.fetched_at DESC
                        LIMIT 1
                        """,
                        this::mapMarketPrice,
                        instrumentId,
                        priceDate)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "The selected date is not a tradable date with stored market data for this instrument."));
    }

    public MarketBarResponse tradableBar(
            String instrumentId, LocalDateTime executionTimestamp) {
        requireInstrument(instrumentId);
        if (executionTimestamp.getSecond() != 0 || executionTimestamp.getNano() != 0) {
            throw new IllegalArgumentException(
                    "executionTimestamp must align to an exact one-minute bar.");
        }
        return jdbc.query(
                        """
                        SELECT b.instrument_id, i.symbol, b.interval_code,
                               b.bar_timestamp, b.open_price, b.high_price,
                               b.low_price, b.close_price, b.volume,
                               b.currency, b.source
                        FROM market_intraday_bar b
                        JOIN instrument i ON i.id = b.instrument_id
                        WHERE b.instrument_id = ?
                          AND b.interval_code = '1min'
                          AND b.bar_timestamp = ?
                          AND b.close_price > 0
                        ORDER BY b.fetched_at DESC
                        LIMIT 1
                        """,
                        (rs, rowNum) -> new MarketBarResponse(
                                rs.getString("instrument_id"),
                                rs.getString("symbol"),
                                rs.getString("interval_code"),
                                rs.getTimestamp("bar_timestamp").toLocalDateTime(),
                                rs.getBigDecimal("open_price"),
                                rs.getBigDecimal("high_price"),
                                rs.getBigDecimal("low_price"),
                                rs.getBigDecimal("close_price"),
                                rs.getObject("volume", Long.class),
                                rs.getString("currency"),
                                rs.getString("source")),
                        instrumentId,
                        executionTimestamp)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "The selected minute is not tradable because no stored one-minute market bar exists."));
    }

    private void requireInstrument(String instrumentId) {
        Integer instrumentCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM instrument WHERE id = ?", Integer.class, instrumentId);
        if (instrumentCount == null || instrumentCount == 0) {
            throw new ResourceNotFoundException("Instrument not found: " + instrumentId);
        }
    }

    private MarketPriceResponse mapMarketPrice(ResultSet rs, int rowNum) throws SQLException {
        LocalDate priceDate = rs.getDate("price_date").toLocalDate();
        return new MarketPriceResponse(
                rs.getString("instrument_id"),
                rs.getString("symbol"),
                priceDate,
                rs.getBigDecimal("close_price"),
                rs.getBigDecimal("adjusted_close"),
                rs.getString("currency"),
                rs.getString("source"),
                toLocalDateTime(rs, "source_timestamp"),
                toLocalDateTime(rs, "fetched_at"),
                calendar.status(priceDate));
    }

    private Optional<SyncRunResponse> currentRunningSync() {
        return querySyncRuns(
                        """
                        SELECT id, provider, status, stage, requested_count, success_count,
                               failure_count, started_at, completed_at, triggered_by,
                               error_summary
                        FROM market_data_sync_run
                        WHERE status = 'RUNNING'
                        ORDER BY started_at
                        LIMIT 1
                        """)
                .stream()
                .findFirst();
    }

    private Optional<SyncRunResponse> findSyncRun(String id) {
        return jdbc.query(
                        """
                        SELECT id, provider, status, stage, requested_count, success_count,
                               failure_count, started_at, completed_at, triggered_by,
                               error_summary
                        FROM market_data_sync_run
                        WHERE id = ?
                        """,
                        this::mapSyncRun,
                        id)
                .stream()
                .findFirst();
    }

    private java.util.List<SyncRunResponse> querySyncRuns(String sql) {
        return jdbc.query(sql, this::mapSyncRun);
    }

    private SyncRunResponse mapSyncRun(ResultSet rs, int rowNum) throws SQLException {
        return new SyncRunResponse(
                rs.getString("id"),
                rs.getString("provider"),
                com.portfoliomanager.domain.SyncStatus.valueOf(rs.getString("status")),
                com.portfoliomanager.domain.SyncStage.valueOf(rs.getString("stage")),
                rs.getInt("requested_count"),
                rs.getInt("success_count"),
                rs.getInt("failure_count"),
                toLocalDateTime(rs, "started_at"),
                toLocalDateTime(rs, "completed_at"),
                com.portfoliomanager.domain.SyncTrigger.valueOf(
                        rs.getString("triggered_by")),
                rs.getString("error_summary"));
    }

    private static LocalDateTime toLocalDateTime(ResultSet rs, String column)
            throws SQLException {
        var timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
