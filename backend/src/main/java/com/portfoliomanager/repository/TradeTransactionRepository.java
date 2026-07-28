package com.portfoliomanager.repository;

import com.portfoliomanager.domain.model.TradeTransaction;
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
}
