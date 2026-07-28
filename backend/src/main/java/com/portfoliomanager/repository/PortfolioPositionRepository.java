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
     * 按组合 ID 查询所有当前持仓，按持仓数量倒序。
     */
    @Query(
            "SELECT p FROM PortfolioPosition p "
                    + "WHERE p.portfolio.id = :portfolioId "
                    + "AND p.quantity > 0 "
                    + "ORDER BY p.quantity DESC")
    List<PortfolioPosition> findByPortfolioId(@Param("portfolioId") String portfolioId);

    /**
     * 按组合ID和标的ID查询持仓，使用 SELECT FOR UPDATE 锁定以支持并发交易。
     * 用于更新持仓前获取锁。
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
