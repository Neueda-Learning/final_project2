package com.portfoliomanager.repository;

import com.portfoliomanager.domain.model.TradeTransaction;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TradeTransactionRepository extends JpaRepository<TradeTransaction, String> {

    /**
     * Checks whether a portfolio has transactions that prevent hard deletion.
     */
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END "
            + "FROM TradeTransaction t WHERE t.portfolio.id = :portfolioId")
    boolean existsByPortfolioId(@Param("portfolioId") String portfolioId);

    /**
     * Finds paginated transaction history ordered by execution time descending.
     */
    @EntityGraph(attributePaths = "instrument")
    Page<TradeTransaction> findByPortfolioIdOrderByExecutedAtDesc(
            @Param("portfolioId") String portfolioId, Pageable pageable);

    /**
     * Check if a trade with the same idempotency key already exists in this portfolio.
     * Returns the existing trade if found, empty if none.
     */
    @Query(
            "SELECT t FROM TradeTransaction t "
                    + "WHERE t.portfolio.id = :portfolioId "
                    + "AND t.idempotencyKey = :idempotencyKey")
    @EntityGraph(attributePaths = "instrument")
    Optional<TradeTransaction> findByPortfolioIdAndIdempotencyKey(
            @Param("portfolioId") String portfolioId,
            @Param("idempotencyKey") String idempotencyKey);

    /**
     * Returns the full transaction history for one instrument in chronological order.
     */
    @Query(
            "SELECT t FROM TradeTransaction t "
                    + "WHERE t.portfolio.id = :portfolioId "
                    + "AND t.instrument.id = :instrumentId "
                    + "ORDER BY t.executedAt ASC, t.createdAt ASC, t.id ASC")
    List<TradeTransaction> findHistoryByPortfolioIdAndInstrumentId(
            @Param("portfolioId") String portfolioId,
            @Param("instrumentId") String instrumentId);
}
