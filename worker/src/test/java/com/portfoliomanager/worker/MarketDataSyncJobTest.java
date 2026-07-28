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
import java.util.Set;
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
        when(jdbc.query(
                        org.mockito.ArgumentMatchers.<String>argThat(
                                sql -> sql != null
                                        && sql.contains("SELECT DISTINCT p.portfolio_id")),
                        any(RowMapper.class),
                        org.mockito.ArgumentMatchers.<Object[]>any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    RowMapper<Object> mapper = invocation.getArgument(1);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getString("portfolio_id"))
                            .thenReturn("portfolio-1");
                    return List.of(mapper.mapRow(resultSet, 0));
                });
        when(jdbc.query(
                        org.mockito.ArgumentMatchers.<String>argThat(
                                sql -> sql != null
                                        && sql.contains("FROM portfolio_summary")),
                        any(RowMapper.class),
                        eq("portfolio-1")))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    RowMapper<Object> mapper = invocation.getArgument(1);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getDate("newest_price_date"))
                            .thenReturn(java.sql.Date.valueOf(LocalDate.of(2026, 7, 24)));
                    when(resultSet.getBigDecimal("priced_market_value"))
                            .thenReturn(new BigDecimal("85420.00000000"));
                    when(resultSet.getBigDecimal("total_cost_basis"))
                            .thenReturn(new BigDecimal("79418.00000000"));
                    when(resultSet.getBigDecimal("priced_cost_basis"))
                            .thenReturn(new BigDecimal("79418.00000000"));
                    when(resultSet.getBigDecimal("unrealized_pnl"))
                            .thenReturn(new BigDecimal("6002.00000000"));
                    when(resultSet.getInt("priced_position_count")).thenReturn(1);
                    when(resultSet.getInt("unpriced_position_count")).thenReturn(0);
                    return List.of(mapper.mapRow(resultSet, 0));
                });

        var job = new MarketDataSyncJob(
                provider,
                jdbc,
                properties,
                Clock.fixed(Instant.parse("2026-07-27T12:00:00Z"), ZoneId.of("UTC")));

        job.processManualRequests();

        verify(provider).fetchDailyCloses(
                eq(List.of("AAPL")),
                eq(LocalDate.of(2026, 6, 28)),
                eq(LocalDate.of(2026, 7, 28)));
        verify(jdbc).update(
                argThat(sql -> sql.contains("INSERT INTO market_price")),
                any(Object[].class));
        verify(jdbc).update(
                argThat(sql -> sql.contains("INSERT INTO portfolio_valuation_snapshot")),
                eq("portfolio-1"),
                eq(LocalDate.of(2026, 7, 24)),
                eq(new BigDecimal("85420.00000000")),
                eq(new BigDecimal("79418.00000000")),
                eq(new BigDecimal("79418.00000000")),
                eq(new BigDecimal("6002.00000000")),
                eq(1),
                eq(0));
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
