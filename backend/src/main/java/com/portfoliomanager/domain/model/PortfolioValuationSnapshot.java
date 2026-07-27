package com.portfoliomanager.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
        name = "portfolio_valuation_snapshot",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_snapshot_portfolio_date",
                columnNames = {"portfolio_id", "valuation_date"}))
public class PortfolioValuationSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Column(name = "valuation_date", nullable = false)
    private LocalDate valuationDate;

    @Column(name = "priced_market_value", nullable = false, precision = 24, scale = 8)
    private BigDecimal pricedMarketValue;

    @Column(name = "total_cost_basis", nullable = false, precision = 24, scale = 8)
    private BigDecimal totalCostBasis;

    @Column(name = "priced_cost_basis", nullable = false, precision = 24, scale = 8)
    private BigDecimal pricedCostBasis;

    @Column(name = "unrealized_pnl", nullable = false, precision = 24, scale = 8)
    private BigDecimal unrealizedPnl;

    @Column(name = "priced_position_count", nullable = false)
    private int pricedPositionCount;

    @Column(name = "unpriced_position_count", nullable = false)
    private int unpricedPositionCount;

    @CreationTimestamp
    @Column(name = "calculated_at", nullable = false, updatable = false)
    private LocalDateTime calculatedAt;

    protected PortfolioValuationSnapshot() {}
}
