package com.portfoliomanager.service;

import com.portfoliomanager.api.ApiModels.AllocationItemResponse;
import com.portfoliomanager.api.ApiModels.DashboardPositionResponse;
import com.portfoliomanager.api.ApiModels.DashboardResponse;
import com.portfoliomanager.api.ApiModels.PerformancePointResponse;
import com.portfoliomanager.api.ApiModels.PerformanceResponse;
import com.portfoliomanager.api.ApiModels.PortfolioInfoResponse;
import com.portfoliomanager.api.ApiModels.PortfolioSummaryResponse;
import com.portfoliomanager.config.WebConfig;
import com.portfoliomanager.domain.AssetType;
import com.portfoliomanager.domain.model.Portfolio;
import com.portfoliomanager.repository.PortfolioRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsService {

    private final JdbcTemplate jdbc;
    private final PortfolioRepository portfolios;
    private final WebConfig config;
    private final MarketCalendarService marketCalendar;

    public AnalyticsService(
            JdbcTemplate jdbc,
            PortfolioRepository portfolios,
            WebConfig config,
            MarketCalendarService marketCalendar) {
        this.jdbc = jdbc;
        this.portfolios = portfolios;
        this.config = config;
        this.marketCalendar = marketCalendar;
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(String portfolioId) {
        Portfolio portfolio = ownedPortfolio(portfolioId);

        PortfolioSummaryResponse summary = jdbc.query(
                        """
                        SELECT portfolio_id, position_count, priced_position_count,
                               unpriced_position_count, priced_market_value,
                               total_cost_basis, priced_cost_basis, unrealized_pnl,
                               return_pct, newest_price_date, oldest_used_price_date
                        FROM portfolio_summary
                        WHERE portfolio_id = ?
                        """,
                        this::mapSummary,
                        portfolioId)
                .stream()
                .findFirst()
                .orElseGet(() -> emptySummary(portfolioId));

        List<DashboardPositionResponse> positions = jdbc.query(
                """
                SELECT pm.instrument_id, pm.symbol, pm.instrument_name, pm.asset_type,
                       pm.quantity, pm.average_cost, pm.cost_basis, pm.close_price,
                       pm.price_date, pm.price_source, pm.market_value,
                       pm.unrealized_pnl, pm.return_pct, pa.allocation_pct
                FROM position_metrics AS pm
                LEFT JOIN portfolio_allocation AS pa
                  ON pa.portfolio_id = pm.portfolio_id
                 AND pa.instrument_id = pm.instrument_id
                WHERE pm.portfolio_id = ?
                ORDER BY pm.market_value IS NULL, pm.market_value DESC, pm.symbol ASC
                """,
                this::mapDashboardPosition,
                portfolioId);

        List<AllocationItemResponse> allocation = positions.stream()
                .filter(position -> position.marketValue() != null)
                .map(position -> new AllocationItemResponse(
                        position.instrumentId(),
                        position.symbol(),
                        position.marketValue(),
                        position.allocationPct()))
                .toList();

        return new DashboardResponse(
                new PortfolioInfoResponse(
                        portfolio.getId(),
                        portfolio.getName(),
                        portfolio.getBaseCurrency()),
                summary,
                positions,
                allocation);
    }

    @Transactional(readOnly = true)
    public PerformanceResponse performance(String portfolioId, LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidDateRangeException("from must be on or before to");
        }

        Portfolio portfolio = ownedPortfolio(portfolioId);
        StringBuilder sql = new StringBuilder(
                """
                SELECT valuation_date, priced_market_value, total_cost_basis,
                       priced_cost_basis, unrealized_pnl,
                       CASE
                           WHEN priced_cost_basis = 0 THEN NULL
                           ELSE ROUND(unrealized_pnl / priced_cost_basis * 100, 8)
                       END AS return_pct,
                       priced_position_count,
                       unpriced_position_count
                FROM portfolio_valuation_snapshot
                WHERE portfolio_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(portfolioId);
        if (from != null) {
            sql.append(" AND valuation_date >= ?");
            args.add(from);
        }
        if (to != null) {
            sql.append(" AND valuation_date <= ?");
            args.add(to);
        }
        sql.append(" ORDER BY valuation_date ASC");

        List<PerformancePointResponse> points = jdbc.query(
                sql.toString(),
                this::mapPerformancePoint,
                args.toArray());

        return new PerformanceResponse(portfolioId, portfolio.getBaseCurrency(), points);
    }

    private Portfolio ownedPortfolio(String portfolioId) {
        return portfolios.findByIdAndUserId(portfolioId, config.getDemoUserId())
                .orElseThrow(() -> new ResourceNotFoundException("PORTFOLIO_NOT_FOUND"));
    }

    private PortfolioSummaryResponse mapSummary(ResultSet rs, int rowNum) throws SQLException {
        return new PortfolioSummaryResponse(
                rs.getString("portfolio_id"),
                rs.getInt("position_count"),
                rs.getInt("priced_position_count"),
                rs.getInt("unpriced_position_count"),
                rs.getBigDecimal("priced_market_value"),
                rs.getBigDecimal("total_cost_basis"),
                rs.getBigDecimal("priced_cost_basis"),
                rs.getBigDecimal("unrealized_pnl"),
                rs.getBigDecimal("return_pct"),
                toLocalDate(rs, "newest_price_date"),
                toLocalDate(rs, "oldest_used_price_date"));
    }

    private DashboardPositionResponse mapDashboardPosition(ResultSet rs, int rowNum)
            throws SQLException {
        LocalDate priceDate = toLocalDate(rs, "price_date");
        return new DashboardPositionResponse(
                rs.getString("instrument_id"),
                rs.getString("symbol"),
                rs.getString("instrument_name"),
                AssetType.valueOf(rs.getString("asset_type")),
                rs.getBigDecimal("quantity"),
                rs.getBigDecimal("average_cost"),
                rs.getBigDecimal("cost_basis"),
                rs.getBigDecimal("close_price"),
                priceDate,
                rs.getString("price_source"),
                marketCalendar.status(priceDate),
                rs.getBigDecimal("market_value"),
                rs.getBigDecimal("unrealized_pnl"),
                rs.getBigDecimal("return_pct"),
                rs.getBigDecimal("allocation_pct"));
    }

    private PerformancePointResponse mapPerformancePoint(ResultSet rs, int rowNum)
            throws SQLException {
        return new PerformancePointResponse(
                toLocalDate(rs, "valuation_date"),
                rs.getBigDecimal("priced_market_value"),
                rs.getBigDecimal("total_cost_basis"),
                rs.getBigDecimal("priced_cost_basis"),
                rs.getBigDecimal("unrealized_pnl"),
                rs.getBigDecimal("return_pct"),
                rs.getInt("priced_position_count"),
                rs.getInt("unpriced_position_count"));
    }

    private static PortfolioSummaryResponse emptySummary(String portfolioId) {
        return new PortfolioSummaryResponse(
                portfolioId,
                0,
                0,
                0,
                java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO,
                null,
                null,
                null);
    }

    private static LocalDate toLocalDate(ResultSet rs, String column) throws SQLException {
        var value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }
}
