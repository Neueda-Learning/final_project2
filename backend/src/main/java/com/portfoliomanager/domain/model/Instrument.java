package com.portfoliomanager.domain.model;

import com.portfoliomanager.domain.AssetType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
        name = "instrument",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_instrument_exchange_symbol",
                columnNames = {"exchange_code", "symbol"}))
public class Instrument {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false)
    private AssetType assetType;

    @Column(name = "exchange_code", nullable = false, length = 32)
    private String exchangeCode;

    @Column(nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "provider_symbol", length = 64)
    private String providerSymbol;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Instrument() {}

    public String getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getName() {
        return name;
    }

    public AssetType getAssetType() {
        return assetType;
    }

    public String getExchangeCode() {
        return exchangeCode;
    }

    public String getCurrency() {
        return currency;
    }

    public boolean isActive() {
        return active;
    }
}
