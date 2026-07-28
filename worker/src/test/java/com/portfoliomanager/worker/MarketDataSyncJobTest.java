package com.portfoliomanager.worker;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.portfoliomanager.worker.provider.DailyPrice;
import com.portfoliomanager.worker.provider.MarketDataProvider;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class MarketDataSyncJobTest {

    @Test
    void manualRunPersistsValidPriceAndCompletesSuccessfully() throws Exception {
        var provider = mock(MarketDataProvider.class);
        var jdbc = mock(JdbcTemplate.class);
        var properties = new MarketDataProperties();
        properties.setProvider("fixture");
        properties.setMaxRetries(0);

        when(provider.name()).thenReturn("fixture");
        when(provider.fetchDailyCloses(
                        eq(List.of("AAPL")),
                        any(LocalDate.class),
                        any(LocalDate.class)))
                .thenReturn(List.of(price()));
        when(jdbc.queryForObject(
                        eq("SELECT GET_LOCK(?, 0)"),
                        eq(Integer.class),
                        eq("portfolio_manager_market_sync")))
                .thenReturn(1);
        when(jdbc.queryForObject(
                        eq("SELECT RELEASE_LOCK(?)"),
                        eq(Integer.class),
                        eq("portfolio_manager_market_sync")))
                .thenReturn(1);
        when(jdbc.query(
                        org.mockito.ArgumentMatchers.<String>argThat(
                                sql -> sql != null
                                        && sql.contains("triggered_by = 'MANUAL'")),
                        any(RowMapper.class)))
                .thenReturn(List.of("run-1"));
        when(jdbc.query(
                        org.mockito.ArgumentMatchers.<String>argThat(
                                sql -> sql != null
                                        && sql.contains("FROM portfolio_position")),
                        any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    RowMapper<Object> mapper = invocation.getArgument(1);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getString("id")).thenReturn("instrument-1");
                    when(resultSet.getString("provider_symbol")).thenReturn("AAPL");
                    when(resultSet.getString("currency")).thenReturn("USD");
                    return List.of(mapper.mapRow(resultSet, 0));
                });

        var job = new MarketDataSyncJob(
                provider,
                jdbc,
                properties,
                Clock.fixed(Instant.parse("2026-07-27T12:00:00Z"), ZoneId.of("UTC")));

        job.processManualRequests();

        verify(provider).fetchDailyCloses(
                eq(List.of("AAPL")), any(LocalDate.class), any(LocalDate.class));
        verify(jdbc).update(
                argThat(sql -> sql.contains("INSERT INTO market_price")),
                any(Object[].class));
        verify(jdbc).update(
                argThat(sql -> sql.contains("SET status = ?")),
                eq("SUCCEEDED"),
                eq(1),
                eq(0),
                eq(null),
                eq("run-1"));
    }

    private DailyPrice price() {
        return new DailyPrice(
                "AAPL",
                LocalDate.of(2026, 7, 24),
                new BigDecimal("212.10"),
                new BigDecimal("215.00"),
                new BigDecimal("211.50"),
                new BigDecimal("213.55"),
                new BigDecimal("213.55"),
                43_210_000L,
                "USD",
                "fixture",
                null);
    }
}
