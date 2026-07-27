package com.portfoliomanager.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class PortfolioPositionId implements Serializable {

    @Column(name = "portfolio_id", length = 36)
    private String portfolioId;

    @Column(name = "instrument_id", length = 36)
    private String instrumentId;

    protected PortfolioPositionId() {}

    public PortfolioPositionId(String portfolioId, String instrumentId) {
        this.portfolioId = portfolioId;
        this.instrumentId = instrumentId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PortfolioPositionId that)) {
            return false;
        }
        return Objects.equals(portfolioId, that.portfolioId)
                && Objects.equals(instrumentId, that.instrumentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(portfolioId, instrumentId);
    }
}
