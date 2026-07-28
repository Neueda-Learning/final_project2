package com.portfoliomanager.service;

import com.portfoliomanager.api.ApiModels.MarketPriceResponse;
import com.portfoliomanager.api.ApiModels.SyncRunResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
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
                            "行情同步任务当前不可用，请稍后重试"));
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
        Integer instrumentCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM instrument WHERE id = ?", Integer.class, instrumentId);
        if (instrumentCount == null || instrumentCount == 0) {
            throw new ResourceNotFoundException("标的不存在: " + instrumentId);
        }

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
                        new ResourceNotFoundException("该标的暂无可用行情: " + instrumentId));
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
