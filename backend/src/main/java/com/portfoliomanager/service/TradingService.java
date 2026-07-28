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
 * Manages instruments, transactions, and current positions.
 *
 * Position updates use SELECT FOR UPDATE for concurrency, and idempotency keys
 * prevent duplicate execution of the same logical request.
 */
@Service
public class TradingService {

    private final InstrumentRepository instruments;
    private final PortfolioRepository portfolios;
    private final TradeTransactionRepository transactions;
    private final PortfolioPositionRepository positions;
    private final MarketDataService marketData;

    public TradingService(
            InstrumentRepository instruments,
            PortfolioRepository portfolios,
            TradeTransactionRepository transactions,
            PortfolioPositionRepository positions,
            MarketDataService marketData) {
        this.instruments = instruments;
        this.portfolios = portfolios;
        this.transactions = transactions;
        this.positions = positions;
        this.marketData = marketData;
    }

    /** Lists active instruments or searches them by symbol or name fragment. */
    public List<InstrumentResponse> searchInstruments(String query, int limit) {
        if (query == null || query.isBlank()) {
            return instruments.findByActiveTrueOrderBySymbol().stream()
                    .limit(limit)
                    .map(TradingService::toInstrumentResponse)
                    .toList();
        }

        return instruments.searchActive(query.trim(), PageRequest.of(0, limit)).stream()
                .map(TradingService::toInstrumentResponse)
                .toList();
    }

    /**
     * Creates a purchase or sale and atomically updates the position.
     *
     * <p>The operation validates its resources, handles idempotent replay, locks
     * the position, validates sale quantity, calculates financial effects, writes
     * the immutable transaction, and updates the projection in one transaction.
     *
     * @param portfolioId portfolio ID
     * @param idempotencyKey idempotency key
     * @param request trade request
     * @return created or replayed transaction
     * @throws IllegalArgumentException when input data is invalid
     * @throws IllegalStateException when a sale exceeds the current position
     */
    @Transactional
    public TransactionResponse createTransaction(
            String portfolioId,
            String idempotencyKey,
            TransactionCreateRequest request) {

        // Validate that the portfolio exists and is active.
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

        // Validate that the instrument exists and is active.
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

        // Replay the original transaction when the idempotency key already exists.
        Optional<TradeTransaction> existingTrade =
                transactions.findByPortfolioIdAndIdempotencyKey(portfolioId, idempotencyKey);
        if (existingTrade.isPresent()) {
            // Idempotent replay returns the existing transaction.
            return toTransactionResponse(existingTrade.get());
        }

        // Resolve the immutable execution price from the selected stored daily close.
        var marketPrice = marketData.tradablePrice(request.instrumentId(), request.priceDate());
        BigDecimal unitPrice = marketPrice.closePrice();
        String currency = marketPrice.currency();
        LocalDateTime executedAt = request.priceDate().atTime(16, 0);
        BigDecimal feeAmount =
                request.feeAmount() == null ? BigDecimal.ZERO : request.feeAmount();

        // Lock the position with SELECT FOR UPDATE.
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

        // Ensure a sale does not exceed the current position.
        if (request.side() == TradeSide.SELL) {
            if (request.quantity().compareTo(currentQuantity) > 0) {
                throw new IllegalStateException(
                        "Insufficient quantity: current="
                                + currentQuantity
                                + ", sell="
                                + request.quantity());
            }
        }

        // Calculate the new quantity, average cost, and realized P&L.
        BigDecimal totalCost;
        BigDecimal totalQuantity;
        BigDecimal newAverageCost;
        BigDecimal newRealizedPnl = currentRealizedPnl;

        if (request.side() == TradeSide.BUY) {
            // Purchase cost = quantity * execution price + fee.
            totalCost =
                    request.quantity()
                            .multiply(unitPrice)
                            .add(feeAmount);
            // Add the purchased quantity to the current position.
            totalQuantity = currentQuantity.add(request.quantity());
            // Calculate weighted average cost.
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
            // Realized P&L = sale quantity * (price - average cost) - fee.
            BigDecimal proceeds =
                    request.quantity()
                            .multiply(unitPrice)
                            .subtract(feeAmount);
            BigDecimal costOfSold = request.quantity().multiply(currentAverageCost);
            BigDecimal pnl = proceeds.subtract(costOfSold);
            newRealizedPnl = currentRealizedPnl.add(pnl);

            // Subtract the sold quantity from the current position.
            totalQuantity = currentQuantity.subtract(request.quantity());
            // Average cost remains unchanged for the remaining position.
            newAverageCost = currentAverageCost;
        }

        // Insert the immutable transaction record.
        String transactionId = UUID.randomUUID().toString();
        TradeTransaction newTransaction =
                new TradeTransaction(
                        transactionId,
                        portfolio,
                        instrument,
                        request.side(),
                        request.quantity(),
                        unitPrice,
                        feeAmount,
                        currency,
                        executedAt,
                        idempotencyKey,
                        request.note());
        transactions.save(newTransaction);

        // Create, update, or remove the current-position projection.
        if (totalQuantity.compareTo(BigDecimal.ZERO) > 0) {
            // A positive quantity requires a position projection.
            if (existingPosition.isPresent()) {
                // Update the existing position.
                PortfolioPosition pos = existingPosition.get();
                pos.setQuantity(totalQuantity);
                pos.setAverageCost(newAverageCost);
                pos.setRealizedPnl(newRealizedPnl);
                positions.save(pos);
            } else {
                // Create a new position.
                PortfolioPosition newPosition =
                        new PortfolioPosition(
                                portfolio, instrument, totalQuantity, newAverageCost, newRealizedPnl);
                positions.save(newPosition);
            }
        } else if (existingPosition.isPresent()) {
            // Remove the projection after a complete liquidation.
            positions.delete(existingPosition.get());
        }

        // The surrounding transaction commits or rolls back the complete change.
        return toTransactionResponse(newTransaction);
    }

    /**
     * Lists paginated transaction history by execution time descending.
     *
     * @param portfolioId portfolio ID
     * @param page one-based page number
     * @param pageSize number of items per page
     * @return paginated transactions
     */
    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> listTransactions(
            String portfolioId, int page, int pageSize) {
        // Validate that the portfolio exists.
        if (!portfolios.existsById(portfolioId)) {
            throw new IllegalArgumentException("Portfolio not found: " + portfolioId);
        }

        // Spring Data uses zero-based page numbers.
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
     * Lists positive current positions ordered by quantity descending.
     *
     * @param portfolioId portfolio ID
     * @return current positions
     */
    @Transactional(readOnly = true)
    public List<PositionResponse> listPositions(String portfolioId) {
        // Validate that the portfolio exists.
        if (!portfolios.existsById(portfolioId)) {
            throw new IllegalArgumentException("Portfolio not found: " + portfolioId);
        }

        return positions.findByPortfolioId(portfolioId).stream()
                .map(TradingService::toPositionResponse)
                .toList();
    }

    // Response mapping helpers.

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
                transaction.getInstrument().getSymbol(),
                transaction.getSide(),
                transaction.getQuantity(),
                transaction.getUnitPrice(),
                transaction.getFeeAmount(),
                transaction.getCurrency(),
                transaction.getExecutedAt(),
                transaction.getNote(),
                transaction.getCreatedAt());
    }

    static PositionResponse toPositionResponse(PortfolioPosition position) {
        Instrument instrument = position.getInstrument();

        return new PositionResponse(
                instrument.getId(),
                instrument.getSymbol(),
                instrument.getName(),
                instrument.getAssetType(),
                position.getQuantity(),
                position.getAverageCost(),
                position.getRealizedPnl(),
                position.getOpenedAt(),
                position.getUpdatedAt());
    }
}
