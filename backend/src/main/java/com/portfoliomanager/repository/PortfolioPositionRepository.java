package com.portfoliomanager.repository;

import com.portfoliomanager.domain.model.PortfolioPosition;
import com.portfoliomanager.domain.model.PortfolioPositionId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface PortfolioPositionRepository
        extends JpaRepository<PortfolioPosition, PortfolioPositionId> {

    /**
     * Finds current positions by portfolio ID, ordered by quantity descending.
     */
    @Query(
            "SELECT p FROM PortfolioPosition p "
                    + "WHERE p.portfolio.id = :portfolioId "
                    + "AND p.quantity > 0 "
                    + "ORDER BY p.quantity DESC")
    List<PortfolioPosition> findByPortfolioId(@Param("portfolioId") String portfolioId);

    /**
     * Finds and locks a position by portfolio and instrument ID for concurrent trading.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "SELECT p FROM PortfolioPosition p "
                    + "WHERE p.portfolio.id = :portfolioId "
                    + "AND p.instrument.id = :instrumentId")
    Optional<PortfolioPosition> findByPortfolioAndInstrumentForUpdate(
            @Param("portfolioId") String portfolioId,
            @Param("instrumentId") String instrumentId);
}
