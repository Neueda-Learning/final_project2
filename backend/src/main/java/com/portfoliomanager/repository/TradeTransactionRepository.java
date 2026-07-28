package com.portfoliomanager.repository;

import com.portfoliomanager.domain.model.TradeTransaction;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TradeTransactionRepository extends JpaRepository<TradeTransaction, String> {

    /**
     * 检查指定组合是否存在任何交易记录。
     * 有交易记录的组合不允许硬删除，只能归档（业务规则）。
     */
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END "
            + "FROM TradeTransaction t WHERE t.portfolio.id = :portfolioId")
    boolean existsByPortfolioId(@Param("portfolioId") String portfolioId);

    /**
     * 按组合 ID 查询交易历史，按成交时间倒序分页。
     */
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
    Optional<TradeTransaction> findByPortfolioIdAndIdempotencyKey(
            @Param("portfolioId") String portfolioId,
            @Param("idempotencyKey") String idempotencyKey);
}
