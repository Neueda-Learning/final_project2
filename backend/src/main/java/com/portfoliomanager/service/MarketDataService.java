package com.portfoliomanager.service;

import com.portfoliomanager.api.ApiModels.MarketPriceResponse;
import com.portfoliomanager.api.ApiModels.SyncRunResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarketDataService {

    private static final String LOCK_NAME = "portfolio_manager_market_sync";

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
        if (running.isPresent()) {
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
            running = currentRunningSync();
            if (running.isPresent()) {
                return running.get();
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
                        SELECT id, provider, status, requested_count, success_count,
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
                        SELECT id, provider, status, requested_count, success_count,
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
                        SELECT id, provider, status, requested_count, success_count,
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
