package com.portfoliomanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.portfoliomanager.api.ApiModels.SyncRunResponse;
import com.portfoliomanager.domain.SyncStage;
import com.portfoliomanager.domain.SyncStatus;
import com.portfoliomanager.domain.SyncTrigger;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class MarketDataServiceTest {

    private static final String GLOBAL_LOCK = "portfolio_manager_market_sync";
    private static final String INSTRUMENT_LOCK_PREFIX = "pmms_instrument_";

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private MarketCalendarService calendar;

    private MarketDataService service;

    @BeforeEach
    void setUp() {
        service = new MarketDataService(jdbc, calendar, "alpaca");
    }

    @Test
    void requestInstrumentSyncQueuesRunEvenWhenAnotherRunIsActive() {
        String instrumentId = "instrument-1";
        String instrumentLock = INSTRUMENT_LOCK_PREFIX + instrumentId.replace("-", "");

        given(jdbc.queryForObject(
                eq("SELECT COUNT(*) FROM instrument WHERE id = ? AND is_active = TRUE"),
                eq(Integer.class),
                eq(instrumentId)))
            .willReturn(1);

        given(jdbc.query(
                org.mockito.ArgumentMatchers.argThat(sql ->
                sql != null && sql.contains("target_instrument_id = ?")),
                any(RowMapper.class),
                eq(instrumentId)))
            .willReturn(List.of());

        given(jdbc.queryForObject(
                eq("SELECT GET_LOCK(?, 0)"),
                eq(Integer.class),
                eq(instrumentLock)))
            .willReturn(1);

        given(jdbc.queryForObject(
                eq("SELECT RELEASE_LOCK(?)"),
                eq(Integer.class),
                eq(instrumentLock)))
            .willReturn(1);

        given(jdbc.query(
                org.mockito.ArgumentMatchers.argThat(sql ->
                sql != null && sql.contains("WHERE id = ?")),
                any(RowMapper.class),
                any()))
            .willAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                RowMapper<SyncRunResponse> mapper = invocation.getArgument(1);
                java.sql.ResultSet rs = org.mockito.Mockito.mock(java.sql.ResultSet.class);
                given(rs.getString("id")).willReturn("run-1");
                given(rs.getString("provider")).willReturn("alpaca");
                given(rs.getString("status")).willReturn("RUNNING");
                given(rs.getString("stage")).willReturn("QUEUED");
                given(rs.getInt("requested_count")).willReturn(0);
                given(rs.getInt("success_count")).willReturn(0);
                given(rs.getInt("failure_count")).willReturn(0);
                given(rs.getTimestamp("started_at"))
                    .willReturn(java.sql.Timestamp.valueOf(LocalDateTime.of(2026, 7, 30, 15, 0)));
                given(rs.getTimestamp("completed_at")).willReturn(null);
                given(rs.getString("triggered_by")).willReturn("MANUAL");
                given(rs.getString("error_summary")).willReturn(null);
                return List.of(mapper.mapRow(rs, 0));
            });

        SyncRunResponse run = service.requestInstrumentSync(instrumentId, false);

        assertThat(run.status()).isEqualTo(SyncStatus.RUNNING);
        assertThat(run.stage()).isEqualTo(SyncStage.QUEUED);
        assertThat(run.triggeredBy()).isEqualTo(SyncTrigger.MANUAL);

        verify(jdbc).update(
                org.mockito.ArgumentMatchers.argThat(sql ->
                        sql.contains("target_instrument_id")),
                any(),
                eq("alpaca"),
                eq(instrumentId));
        verify(jdbc, never()).queryForObject(
                eq("SELECT GET_LOCK(?, 0)"),
                eq(Integer.class),
                eq(GLOBAL_LOCK));
    }

    @Test
    void requestInstrumentSync_doesNotDependOnGlobalLockForNonForceRequests() {
        String instrumentId = "instrument-2";
        String instrumentLock = INSTRUMENT_LOCK_PREFIX + instrumentId.replace("-", "");

        given(jdbc.queryForObject(
                eq("SELECT COUNT(*) FROM instrument WHERE id = ? AND is_active = TRUE"),
                eq(Integer.class),
                eq(instrumentId)))
            .willReturn(1);

        given(jdbc.query(
                org.mockito.ArgumentMatchers.argThat(sql ->
                        sql != null && sql.contains("target_instrument_id = ?")),
                any(RowMapper.class),
                eq(instrumentId)))
            .willReturn(List.of());

        given(jdbc.queryForObject(
                eq("SELECT GET_LOCK(?, 0)"),
                eq(Integer.class),
                eq(instrumentLock)))
            .willReturn(1);

        given(jdbc.queryForObject(
                eq("SELECT RELEASE_LOCK(?)"),
                eq(Integer.class),
                eq(instrumentLock)))
            .willReturn(1);

        given(jdbc.query(
                org.mockito.ArgumentMatchers.argThat(sql ->
                        sql != null && sql.contains("WHERE id = ?")),
                any(RowMapper.class),
                any()))
            .willAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                RowMapper<SyncRunResponse> mapper = invocation.getArgument(1);
                java.sql.ResultSet rs = org.mockito.Mockito.mock(java.sql.ResultSet.class);
                given(rs.getString("id")).willReturn("run-2");
                given(rs.getString("provider")).willReturn("alpaca");
                given(rs.getString("status")).willReturn("RUNNING");
                given(rs.getString("stage")).willReturn("QUEUED");
                given(rs.getInt("requested_count")).willReturn(0);
                given(rs.getInt("success_count")).willReturn(0);
                given(rs.getInt("failure_count")).willReturn(0);
                given(rs.getTimestamp("started_at"))
                    .willReturn(java.sql.Timestamp.valueOf(LocalDateTime.of(2026, 7, 30, 15, 10)));
                given(rs.getTimestamp("completed_at")).willReturn(null);
                given(rs.getString("triggered_by")).willReturn("MANUAL");
                given(rs.getString("error_summary")).willReturn(null);
                return List.of(mapper.mapRow(rs, 0));
            });

        SyncRunResponse run = service.requestInstrumentSync(instrumentId, false);

        assertThat(run.id()).isEqualTo("run-2");
        assertThat(run.status()).isEqualTo(SyncStatus.RUNNING);
        verify(jdbc, never()).queryForObject(
                eq("SELECT GET_LOCK(?, 0)"),
                eq(Integer.class),
                eq(GLOBAL_LOCK));
    }
}
