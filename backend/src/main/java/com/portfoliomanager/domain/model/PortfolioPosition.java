package com.portfoliomanager.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "portfolio_position")
public class PortfolioPosition {

    @EmbeddedId
    private PortfolioPositionId id;

    @MapsId("portfolioId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id")
    private Portfolio portfolio;

    @MapsId("instrumentId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id")
    private Instrument instrument;

    @Column(nullable = false, precision = 28, scale = 8)
    private BigDecimal quantity;

    @Column(name = "average_cost", nullable = false, precision = 20, scale = 8)
    private BigDecimal averageCost;

    @Column(name = "realized_pnl", nullable = false, precision = 20, scale = 8)
    private BigDecimal realizedPnl = BigDecimal.ZERO;

    @Version
    @Column(nullable = false)
    private int version = 1;

    @CreationTimestamp
    @Column(name = "opened_at", nullable = false, updatable = false)
    private LocalDateTime openedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected PortfolioPosition() {}

    public PortfolioPosition(
            Portfolio portfolio,
            Instrument instrument,
            BigDecimal quantity,
            BigDecimal averageCost,
            BigDecimal realizedPnl) {
        this.id = new PortfolioPositionId(portfolio.getId(), instrument.getId());
        this.portfolio = portfolio;
        this.instrument = instrument;
        this.quantity = quantity;
        this.averageCost = averageCost;
        this.realizedPnl = realizedPnl;
    }

    public PortfolioPositionId getId() {
        return id;
    }

    public Portfolio getPortfolio() {
        return portfolio;
    }

    public Instrument getInstrument() {
        return instrument;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getAverageCost() {
        return averageCost;
    }

    public BigDecimal getRealizedPnl() {
        return realizedPnl;
    }

    public int getVersion() {
        return version;
    }

    public LocalDateTime getOpenedAt() {
        return openedAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public void setAverageCost(BigDecimal averageCost) {
        this.averageCost = averageCost;
    }

    public void setRealizedPnl(BigDecimal realizedPnl) {
        this.realizedPnl = realizedPnl;
    }
}
