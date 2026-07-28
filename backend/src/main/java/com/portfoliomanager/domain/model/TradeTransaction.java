package com.portfoliomanager.domain.model;

import com.portfoliomanager.domain.TradeSide;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
        name = "trade_transaction",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_trade_portfolio_idempotency",
                columnNames = {"portfolio_id", "idempotency_key"}))
public class TradeTransaction {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id", nullable = false)
    private Instrument instrument;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TradeSide side;

    @Column(nullable = false, precision = 28, scale = 8)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 20, scale = 8)
    private BigDecimal unitPrice;

    @Column(name = "fee_amount", nullable = false, precision = 20, scale = 8)
    private BigDecimal feeAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(length = 500)
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected TradeTransaction() {}

    public TradeTransaction(
            String id,
            Portfolio portfolio,
            Instrument instrument,
            TradeSide side,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal feeAmount,
            String currency,
            LocalDateTime executedAt,
            String idempotencyKey,
            String note) {
        this.id = id;
        this.portfolio = portfolio;
        this.instrument = instrument;
        this.side = side;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.feeAmount = feeAmount;
        this.currency = currency;
        this.executedAt = executedAt;
        this.idempotencyKey = idempotencyKey;
        this.note = note;
    }

    public String getId() {
        return id;
    }

    public Portfolio getPortfolio() {
        return portfolio;
    }

    public Instrument getInstrument() {
        return instrument;
    }

    public TradeSide getSide() {
        return side;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getFeeAmount() {
        return feeAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public LocalDateTime getExecutedAt() {
        return executedAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getNote() {
        return note;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
