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
        name = "market_price",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_market_price_instrument_date_source",
                columnNames = {"instrument_id", "price_date", "source"}))
public class MarketPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id", nullable = false)
    private Instrument instrument;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sync_run_id")
    private MarketDataSyncRun syncRun;

    @Column(name = "price_date", nullable = false)
    private LocalDate priceDate;

    @Column(name = "open_price", precision = 20, scale = 8)
    private BigDecimal openPrice;

    @Column(name = "high_price", precision = 20, scale = 8)
    private BigDecimal highPrice;

    @Column(name = "low_price", precision = 20, scale = 8)
    private BigDecimal lowPrice;

    @Column(name = "close_price", nullable = false, precision = 20, scale = 8)
    private BigDecimal closePrice;

    @Column(name = "adjusted_close", precision = 20, scale = 8)
    private BigDecimal adjustedClose;

    @Column(precision = 28)
    private BigDecimal volume;

    @Column(nullable = false, length = 3)
    private String currency = "USD";

    @Column(nullable = false, length = 50)
    private String source;

    @Column(name = "source_timestamp")
    private LocalDateTime sourceTimestamp;

    @CreationTimestamp
    @Column(name = "fetched_at", nullable = false, updatable = false)
    private LocalDateTime fetchedAt;

    protected MarketPrice() {}
}
