package com.portfoliomanager.service;

import com.portfoliomanager.api.ApiModels.InstrumentResponse;
import com.portfoliomanager.api.ApiModels.PageResponse;
import com.portfoliomanager.api.ApiModels.PositionResponse;
import com.portfoliomanager.api.ApiModels.TransactionCreateRequest;
import com.portfoliomanager.api.ApiModels.TransactionResponse;
import com.portfoliomanager.domain.TradeSide;
import com.portfoliomanager.domain.model.Instrument;
import com.portfoliomanager.domain.model.Portfolio;
import com.portfoliomanager.domain.model.PortfolioPosition;
import com.portfoliomanager.domain.model.TradeTransaction;
import com.portfoliomanager.repository.InstrumentRepository;
import com.portfoliomanager.repository.PortfolioPositionRepository;
import com.portfoliomanager.repository.PortfolioRepository;
import com.portfoliomanager.repository.TradeTransactionRepository;
import jakarta.persistence.OptimisticLockException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 交易和持仓管理服务。
 * 
 * 实现标的搜索、交易记录和原子持仓更新。
 * 使用 SELECT FOR UPDATE 处理并发交易，确保持仓的一致性。
 * 幂等键保证相同请求不会重复执行。
 */
@Service
public class TradingService {

    private final InstrumentRepository instruments;
    private final PortfolioRepository portfolios;
    private final TradeTransactionRepository transactions;
    private final PortfolioPositionRepository positions;

    public TradingService(
            InstrumentRepository instruments,
            PortfolioRepository portfolios,
            TradeTransactionRepository transactions,
            PortfolioPositionRepository positions) {
        this.instruments = instruments;
        this.portfolios = portfolios;
        this.transactions = transactions;
        this.positions = positions;
    }

    /**
     * 按资产类型搜索活跃标的。
     * 仅支持 STOCK 或 ETF 搜索。
     */
    public List<InstrumentResponse> searchInstruments(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        String trimmedQuery = query.trim().toUpperCase();

        // Only support filtering by assetType: STOCK or ETF
        if ("STOCK".equals(trimmedQuery) || "ETF".equals(trimmedQuery)) {
            return instruments.searchActiveByAssetType(trimmedQuery).stream()
                    .map(TradingService::toInstrumentResponse)
                    .toList();
        }

        return List.of();
    }

    /**
     * 创建买入或卖出交易，同时原子更新持仓。
     *
     * <p>业务流程：
     * 1. 验证组合存在且可用
     * 2. 验证标的存在且活跃
     * 3. 检查幂等键冲突
     * 4. 使用 SELECT FOR UPDATE 锁定持仓
     * 5. 验证卖出数量不超过当前持仓
     * 6. 计算新数量、平均成本和已实现盈亏
     * 7. 插入不可变交易记录
     * 8. 新增或更新持仓投影
     * 9. 在同一事务提交或整体回滚
     *
     * @param portfolioId 组合 ID
     * @param idempotencyKey 幂等键
     * @param request 交易请求
     * @return 交易响应
     * @throws IllegalArgumentException 如果输入数据无效
     * @throws IllegalStateException 如果卖出数量超过持仓
     */
    @Transactional
    public TransactionResponse createTransaction(
            String portfolioId,
            String idempotencyKey,
            TransactionCreateRequest request) {

        // 1. 验证组合存在且可用（不能被归档）
        Portfolio portfolio =
                portfolios
                        .findById(portfolioId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Portfolio not found: " + portfolioId));
        if (portfolio.isArchived()) {
            throw new IllegalArgumentException("Portfolio is archived: " + portfolioId);
        }

        // 2. 验证标的存在且活跃
        Instrument instrument =
                instruments
                        .findById(request.instrumentId())
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Instrument not found: "
                                                        + request.instrumentId()));
        if (!instrument.isActive()) {
            throw new IllegalArgumentException(
                    "Instrument is inactive: " + request.instrumentId());
        }

        // 3. 检查幂等键冲突 - 如果相同幂等键的交易已存在，返回原有交易
        Optional<TradeTransaction> existingTrade =
                transactions.findByPortfolioIdAndIdempotencyKey(portfolioId, idempotencyKey);
        if (existingTrade.isPresent()) {
            // 幂等重放：返回已有的交易
            return toTransactionResponse(existingTrade.get());
        }

        // 4. 使用 SELECT FOR UPDATE 锁定持仓
        Optional<PortfolioPosition> existingPosition =
                positions.findByPortfolioAndInstrumentForUpdate(
                        portfolioId, request.instrumentId());

        BigDecimal currentQuantity = BigDecimal.ZERO;
        BigDecimal currentAverageCost = BigDecimal.ZERO;
        BigDecimal currentRealizedPnl = BigDecimal.ZERO;

        if (existingPosition.isPresent()) {
            PortfolioPosition pos = existingPosition.get();
            currentQuantity = pos.getQuantity();
            currentAverageCost = pos.getAverageCost();
            currentRealizedPnl = pos.getRealizedPnl();
        }

        // 5. 验证卖出数量不超过当前持仓
        if (request.side() == TradeSide.SELL) {
            if (request.quantity().compareTo(currentQuantity) > 0) {
                throw new IllegalStateException(
                        "Insufficient quantity: current="
                                + currentQuantity
                                + ", sell="
                                + request.quantity());
            }
        }

        // 6. 计算新持仓数量、平均成本和已实现盈亏
        BigDecimal totalCost;
        BigDecimal totalQuantity;
        BigDecimal newAverageCost;
        BigDecimal newRealizedPnl = currentRealizedPnl;

        if (request.side() == TradeSide.BUY) {
            // 买入：成本 = 数量 * 成交价 + 手续费
            totalCost =
                    request.quantity()
                            .multiply(request.unitPrice())
                            .add(request.feeAmount());
            // 新持仓 = 旧持仓 + 买入数量
            totalQuantity = currentQuantity.add(request.quantity());
            // 加权平均成本
            if (totalQuantity.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal totalCostBasis =
                        currentQuantity
                                .multiply(currentAverageCost)
                                .add(totalCost);
                newAverageCost = totalCostBasis.divide(totalQuantity, 8, BigDecimal.ROUND_HALF_UP);
            } else {
                newAverageCost = BigDecimal.ZERO;
            }
        } else {
            // 卖出：已实现盈亏 = 卖出数量 * (成交价 - 平均成本) - 手续费
            BigDecimal proceeds =
                    request.quantity()
                            .multiply(request.unitPrice())
                            .subtract(request.feeAmount());
            BigDecimal costOfSold = request.quantity().multiply(currentAverageCost);
            BigDecimal pnl = proceeds.subtract(costOfSold);
            newRealizedPnl = currentRealizedPnl.add(pnl);

            // 新持仓 = 旧持仓 - 卖出数量
            totalQuantity = currentQuantity.subtract(request.quantity());
            // 卖出后平均成本不变（剩余持仓）
            newAverageCost = currentAverageCost;
        }

        // 7. 插入不可变交易记录
        String transactionId = UUID.randomUUID().toString();
        TradeTransaction newTransaction =
                new TradeTransaction(
                        transactionId,
                        portfolio,
                        instrument,
                        request.side(),
                        request.quantity(),
                        request.unitPrice(),
                        request.feeAmount(),
                        request.currency(),
                        request.executedAt(),
                        idempotencyKey,
                        request.note());
        transactions.save(newTransaction);

        // 8. 新增或更新持仓投影
        if (totalQuantity.compareTo(BigDecimal.ZERO) > 0) {
            // 有持仓，新增或更新
            if (existingPosition.isPresent()) {
                // 更新现有持仓
                PortfolioPosition pos = existingPosition.get();
                pos.setQuantity(totalQuantity);
                pos.setAverageCost(newAverageCost);
                pos.setRealizedPnl(newRealizedPnl);
                positions.save(pos);
            } else {
                // 新增持仓
                PortfolioPosition newPosition =
                        new PortfolioPosition(
                                portfolio, instrument, totalQuantity, newAverageCost, newRealizedPnl);
                positions.save(newPosition);
            }
        } else if (existingPosition.isPresent()) {
            // 无持仓且之前有持仓，删除（卖光了）
            positions.delete(existingPosition.get());
        }

        // 9. 事务自动提交或回滚
        return toTransactionResponse(newTransaction);
    }

    /**
     * 分页查询交易历史，按成交时间倒序。
     *
     * @param portfolioId 组合 ID
     * @param page 页码（1-based）
     * @param pageSize 每页数量
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> listTransactions(
            String portfolioId, int page, int pageSize) {
        // 验证组合存在
        if (!portfolios.existsById(portfolioId)) {
            throw new IllegalArgumentException("Portfolio not found: " + portfolioId);
        }

        // 使用 0-based 页码查询
        Pageable pageable = PageRequest.of(page - 1, pageSize);
        Page<TradeTransaction> transactionPage =
                transactions.findByPortfolioIdOrderByExecutedAtDesc(portfolioId, pageable);

        List<TransactionResponse> items =
                transactionPage.getContent().stream()
                        .map(TradingService::toTransactionResponse)
                        .toList();

        return new PageResponse<>(
                items, page, pageSize, transactionPage.getTotalElements());
    }

    /**
     * 查询组合的当前持仓（数量 > 0），按持仓数量倒序。
     *
     * @param portfolioId 组合 ID
     * @return 持仓列表
     */
    @Transactional(readOnly = true)
    public List<PositionResponse> listPositions(String portfolioId) {
        // 验证组合存在
        if (!portfolios.existsById(portfolioId)) {
            throw new IllegalArgumentException("Portfolio not found: " + portfolioId);
        }

        return positions.findByPortfolioId(portfolioId).stream()
                .map(TradingService::toPositionResponse)
                .toList();
    }

    // ==================== 转换方法 ====================

    static InstrumentResponse toInstrumentResponse(Instrument instrument) {
        return new InstrumentResponse(
                instrument.getId(),
                instrument.getSymbol(),
                instrument.getName(),
                instrument.getAssetType(),
                instrument.getExchangeCode(),
                instrument.getCurrency(),
                instrument.isActive());
    }

    static TransactionResponse toTransactionResponse(TradeTransaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getPortfolio().getId(),
                transaction.getInstrument().getId(),
                transaction.getSide(),
                transaction.getQuantity(),
                transaction.getUnitPrice(),
                transaction.getFeeAmount(),
                transaction.getCurrency(),
                transaction.getExecutedAt(),
                transaction.getIdempotencyKey(),
                transaction.getNote(),
                transaction.getCreatedAt());
    }

    static PositionResponse toPositionResponse(PortfolioPosition position) {
        Instrument instrument = position.getInstrument();
        InstrumentResponse instrumentResponse = toInstrumentResponse(instrument);

        // 计算市值和未实现盈亏（模拟：以平均成本作为当前价格）
        // 实际应该从行情数据中获取
        BigDecimal marketValue = position.getQuantity().multiply(position.getAverageCost());
        BigDecimal costBasis = position.getQuantity().multiply(position.getAverageCost());
        BigDecimal unrealizedPnl = BigDecimal.ZERO; // 模拟数据
        BigDecimal returnPct = BigDecimal.ZERO; // 模拟数据

        return new PositionResponse(
                position.getPortfolio().getId(),
                instrumentResponse,
                position.getQuantity(),
                position.getAverageCost(),
                position.getRealizedPnl(),
                costBasis,
                position.getAverageCost(), // closePrice 使用平均成本作为模拟
                marketValue,
                unrealizedPnl,
                returnPct,
                position.getUpdatedAt().toLocalDate(), // priceDate: 使用最后更新日期
                null); // priceStatus: TODO 从行情数据中获取
    }
}
