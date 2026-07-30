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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
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

    private static final ZoneId BEIJING_ZONE = ZoneId.of("Asia/Shanghai");

    private static final Comparator<TradeTransaction> TRADE_HISTORY_ORDER =
        Comparator.comparing(TradeTransaction::getExecutedAt)
            .thenComparing(
                TradeTransaction::getCreatedAt,
                Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(TradeTransaction::getId);

    private final InstrumentRepository instruments;
    private final PortfolioRepository portfolios;
    private final TradeTransactionRepository transactions;
    private final PortfolioPositionRepository positions;
    private final JdbcTemplate jdbc;
    private final UsMarketInstrumentSearchService marketSearch;

    @Autowired
    public TradingService(
            InstrumentRepository instruments,
            PortfolioRepository portfolios,
            TradeTransactionRepository transactions,
            PortfolioPositionRepository positions,
            JdbcTemplate jdbc,
            UsMarketInstrumentSearchService marketSearch) {
        this.instruments = instruments;
        this.portfolios = portfolios;
        this.transactions = transactions;
        this.positions = positions;
        this.jdbc = jdbc;
        this.marketSearch = marketSearch;
    }

    TradingService(
            InstrumentRepository instruments,
            PortfolioRepository portfolios,
            TradeTransactionRepository transactions,
            PortfolioPositionRepository positions,
            JdbcTemplate jdbc) {
        this(instruments, portfolios, transactions, positions, jdbc, null);
    }

    /** Lists active instruments or searches them by symbol or name fragment. */
    public List<InstrumentResponse> searchInstruments(String query, int limit) {
        if (query == null || query.isBlank()) {
            return instruments.findByActiveTrueOrderBySymbol().stream()
                    .limit(limit)
                    .map(TradingService::toInstrumentResponse)
                    .toList();
        }

        String normalizedQuery = query.trim();
        List<InstrumentResponse> localResults = instruments.searchActive(
                        normalizedQuery, PageRequest.of(0, limit))
                .stream()
                .map(TradingService::toInstrumentResponse)
                .toList();

        if (localResults.size() >= limit || marketSearch == null) {
            return localResults;
        }

        List<UsMarketInstrumentSearchService.DiscoveredInstrument> discovered =
                marketSearch.search(normalizedQuery, Math.max(limit * 2, 20));
        if (!discovered.isEmpty()) {
            upsertDiscoveredInstruments(discovered);
            localResults = instruments.searchActive(normalizedQuery, PageRequest.of(0, limit)).stream()
                    .map(TradingService::toInstrumentResponse)
                    .toList();
        }

        return localResults;
    }

    private void upsertDiscoveredInstruments(
            List<UsMarketInstrumentSearchService.DiscoveredInstrument> discovered) {
        String sql =
                """
                INSERT INTO instrument (
                    id, symbol, name, asset_type, exchange_code,
                    currency, provider_symbol, is_active
                ) VALUES (?, ?, ?, ?, ?, ?, ?, TRUE)
                ON DUPLICATE KEY UPDATE
                    name = VALUES(name),
                    asset_type = VALUES(asset_type),
                    currency = VALUES(currency),
                    provider_symbol = VALUES(provider_symbol),
                    is_active = TRUE,
                    updated_at = CURRENT_TIMESTAMP(6)
                """;

        Set<String> seen = new HashSet<>();
        for (UsMarketInstrumentSearchService.DiscoveredInstrument instrument : discovered) {
            String symbol = clampUpper(instrument.symbol(), 32);
            String exchangeCode = clampUpper(instrument.exchangeCode(), 32);
            String key = exchangeCode + "|" + symbol;
            if (symbol.isBlank() || exchangeCode.isBlank() || !seen.add(key)) {
                continue;
            }

            String name = clamp(instrument.name(), 200);
            String currency = clampUpper(instrument.currency(), 3);
            String providerSymbol = clamp(
                    instrument.providerSymbol() == null || instrument.providerSymbol().isBlank()
                            ? symbol
                            : instrument.providerSymbol().trim(),
                    64);
            String assetType = "ETF".equalsIgnoreCase(instrument.assetType())
                    ? "ETF"
                    : "STOCK";

            jdbc.update(
                    sql,
                    UUID.randomUUID().toString(),
                    symbol,
                    name,
                    assetType,
                    exchangeCode,
                    currency,
                    providerSymbol);
        }
    }

    private String clamp(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        return trimmed.length() <= maxLength
                ? trimmed
                : trimmed.substring(0, maxLength);
    }

    private String clampUpper(String value, int maxLength) {
        String clamped = clamp(value, maxLength);
        if (clamped.isEmpty()) {
            return "";
        }
        return clamped.toUpperCase(Locale.ROOT);
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

        LocalDate beijingToday = LocalDate.now(BEIJING_ZONE);
        if (request.tradeDate().isAfter(beijingToday)) {
            throw new IllegalArgumentException(
                "tradeDate must be on or before today (Asia/Shanghai)");
        }

        // Replay the original transaction when the idempotency key already exists.
        Optional<TradeTransaction> existingTrade =
                transactions.findByPortfolioIdAndIdempotencyKey(portfolioId, idempotencyKey);
        if (existingTrade.isPresent()) {
            // Idempotent replay returns the existing transaction.
            return toTransactionResponse(existingTrade.get());
        }

        // The user records the actual execution price; market history is not a trade dependency.
        BigDecimal unitPrice = request.unitPrice();
        String currency = instrument.getCurrency();
        LocalDateTime executedAt = request.tradeDate().atTime(16, 0);
        BigDecimal feeAmount =
                request.feeAmount() == null ? BigDecimal.ZERO : request.feeAmount();

        // Lock the position with SELECT FOR UPDATE.
        Optional<PortfolioPosition> existingPosition =
                positions.findByPortfolioAndInstrumentForUpdate(
                        portfolioId, request.instrumentId());
        List<TradeTransaction> tradeHistory = transactions.findHistoryByPortfolioIdAndInstrumentId(
                portfolioId,
                request.instrumentId());

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

        PositionState nextState = replayTradeHistory(tradeHistory, newTransaction);
        transactions.save(newTransaction);

        syncCurrentPosition(existingPosition, portfolio, instrument, nextState);

        // Rebuild valuation snapshots from trade date to today to reflect historical performance
        rebuildValuationSnapshotsAfterTrade(portfolioId, request.tradeDate());

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
        // Spring Data uses zero-based page numbers.
        Pageable pageable = PageRequest.of(page - 1, pageSize);
        Page<TradeTransaction> transactionPage =
                transactions.findByPortfolioIdOrderByExecutedAtDesc(portfolioId, pageable);

        // Avoid a separate round trip on the normal, non-empty path while preserving
        // the existing not-found response for empty or out-of-range pages.
        if (transactionPage.isEmpty() && !portfolios.existsById(portfolioId)) {
            throw new IllegalArgumentException("Portfolio not found: " + portfolioId);
        }

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
        List<PortfolioPosition> portfolioPositions = positions.findByPortfolioId(portfolioId);

        // A populated result already proves the portfolio exists. Only pay for a
        // separate existence query when an empty result is ambiguous.
        if (portfolioPositions.isEmpty() && !portfolios.existsById(portfolioId)) {
            throw new IllegalArgumentException("Portfolio not found: " + portfolioId);
        }

        return portfolioPositions.stream()
                .map(TradingService::toPositionResponse)
                .toList();
    }

    private PositionState replayTradeHistory(
            List<TradeTransaction> existingTrades,
            TradeTransaction pendingTrade) {
        List<TradeTransaction> orderedTrades = new ArrayList<>(existingTrades);
        orderedTrades.add(pendingTrade);
        orderedTrades.sort(TRADE_HISTORY_ORDER);

        BigDecimal quantity = BigDecimal.ZERO;
        BigDecimal averageCost = BigDecimal.ZERO;
        BigDecimal realizedPnl = BigDecimal.ZERO;

        for (TradeTransaction trade : orderedTrades) {
            if (trade.getSide() == TradeSide.BUY) {
                BigDecimal totalCost = trade.getQuantity()
                        .multiply(trade.getUnitPrice())
                        .add(trade.getFeeAmount());
                BigDecimal totalQuantity = quantity.add(trade.getQuantity());
                BigDecimal totalCostBasis = quantity.multiply(averageCost).add(totalCost);

                quantity = totalQuantity;
                averageCost = totalCostBasis.divide(totalQuantity, 8, RoundingMode.HALF_UP);
                continue;
            }

            if (trade.getQuantity().compareTo(quantity) > 0) {
                throw new ConflictException("INSUFFICIENT_QUANTITY");
            }

            BigDecimal proceeds = trade.getQuantity()
                    .multiply(trade.getUnitPrice())
                    .subtract(trade.getFeeAmount());
            BigDecimal costOfSold = trade.getQuantity().multiply(averageCost);
            realizedPnl = realizedPnl.add(proceeds.subtract(costOfSold));
            quantity = quantity.subtract(trade.getQuantity());

            if (quantity.signum() == 0) {
                averageCost = BigDecimal.ZERO;
            }
        }

        return new PositionState(quantity, averageCost, realizedPnl);
    }

    private void syncCurrentPosition(
            Optional<PortfolioPosition> existingPosition,
            Portfolio portfolio,
            Instrument instrument,
            PositionState nextState) {
        if (nextState.quantity().signum() > 0) {
            if (existingPosition.isPresent()) {
                PortfolioPosition position = existingPosition.get();
                position.setQuantity(nextState.quantity());
                position.setAverageCost(nextState.averageCost());
                position.setRealizedPnl(nextState.realizedPnl());
                positions.save(position);
                return;
            }

            positions.save(new PortfolioPosition(
                    portfolio,
                    instrument,
                    nextState.quantity(),
                    nextState.averageCost(),
                    nextState.realizedPnl()));
            return;
        }

        existingPosition.ifPresent(positions::delete);
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

    /**
     * Updates or creates a valuation snapshot for today with the current portfolio state.
     * This ensures that the performance chart reflects recent transaction changes.
     */
    private void updateValuationSnapshotForToday(String portfolioId) {
        LocalDate today = LocalDate.now(BEIJING_ZONE);
        try {
            // Insert or update today's valuation snapshot for this portfolio
            jdbc.update(
                    """
                    INSERT INTO portfolio_valuation_snapshot
                    (portfolio_id, valuation_date, priced_market_value, total_cost_basis,
                     priced_cost_basis, unrealized_pnl, priced_position_count, unpriced_position_count,
                     calculated_at)
                    SELECT
                        portfolio_id, ?, 
                        COALESCE(SUM(market_value), 0),
                        COALESCE(SUM(cost_basis), 0),
                        COALESCE(SUM(CASE WHEN market_value IS NOT NULL THEN cost_basis ELSE 0 END), 0),
                        COALESCE(SUM(unrealized_pnl), 0),
                        COUNT(CASE WHEN market_value IS NOT NULL THEN instrument_id END),
                        COUNT(*) - COUNT(CASE WHEN market_value IS NOT NULL THEN instrument_id END),
                        CURRENT_TIMESTAMP(6)
                    FROM position_metrics
                    WHERE portfolio_id = ?
                    GROUP BY portfolio_id
                    ON DUPLICATE KEY UPDATE
                        priced_market_value = VALUES(priced_market_value),
                        total_cost_basis = VALUES(total_cost_basis),
                        priced_cost_basis = VALUES(priced_cost_basis),
                        unrealized_pnl = VALUES(unrealized_pnl),
                        priced_position_count = VALUES(priced_position_count),
                        unpriced_position_count = VALUES(unpriced_position_count),
                        calculated_at = CURRENT_TIMESTAMP(6)
                    """,
                    today,
                    portfolioId);
        } catch (Exception e) {
            // Snapshot update is best-effort; do not fail the transaction if it fails
        }
    }

    /**
     * Rebuilds valuation snapshots from a trade date to today using market price history.
     * This reconstructs daily portfolio values to show accurate historical performance.
     */
    private void rebuildValuationSnapshotsAfterTrade(String portfolioId, LocalDate tradeDate) {
        try {
            LocalDate today = LocalDate.now(BEIJING_ZONE);
            
            // Delete existing snapshots for this portfolio from trade date onward
            jdbc.update(
                    """
                    DELETE FROM portfolio_valuation_snapshot
                    WHERE portfolio_id = ? AND valuation_date >= ?
                    """,
                    portfolioId,
                    tradeDate);
            
            // Generate snapshots for all business days from trade date to today
            LocalDate current = tradeDate;
            while (!current.isAfter(today)) {
                // Check if it's a weekday (Monday-Friday)
                int dayOfWeek = current.getDayOfWeek().getValue();
                if (dayOfWeek >= 1 && dayOfWeek <= 5) {
                    try {
                        insertHistoricalValuationSnapshot(portfolioId, current);
                    } catch (Exception e) {
                        // Skip this date if calculation fails (e.g., duplicate key)
                    }
                }
                current = current.plusDays(1);
            }
        } catch (Exception e) {
            // Valuation rebuild is best-effort; do not fail the transaction
        }
    }

    /**
     * Calculates and inserts a historical valuation snapshot for a specific date.
     * Reconstructs the portfolio state as of that date from transaction history.
     * Uses the latest available price (on or before the valuation date) for valuation.
     */
    private void insertHistoricalValuationSnapshot(String portfolioId, LocalDate valuationDate) {
        try {
            jdbc.update(
                    """
                    INSERT INTO portfolio_valuation_snapshot
                    (portfolio_id, valuation_date, priced_market_value, total_cost_basis,
                     priced_cost_basis, unrealized_pnl, priced_position_count, unpriced_position_count,
                     calculated_at)
                    SELECT
                        ?,
                        ?,
                        COALESCE(SUM(CASE WHEN mp.close_price IS NOT NULL THEN hist.quantity * mp.close_price ELSE hist.quantity * hist.average_cost END), 0),
                        COALESCE(SUM(hist.quantity * hist.average_cost), 0),
                        COALESCE(SUM(hist.quantity * hist.average_cost), 0),
                        COALESCE(SUM(CASE WHEN mp.close_price IS NOT NULL THEN (hist.quantity * mp.close_price) - (hist.quantity * hist.average_cost) ELSE 0 END), 0),
                        COALESCE(COUNT(CASE WHEN mp.close_price IS NOT NULL THEN 1 END), 0),
                        COALESCE(COUNT(*) - COUNT(CASE WHEN mp.close_price IS NOT NULL THEN 1 END), 0),
                        CURRENT_TIMESTAMP(6)
                    FROM (
                        SELECT
                            i.id,
                            COALESCE(SUM(CASE WHEN t.side = 'BUY' THEN t.quantity WHEN t.side = 'SELL' THEN -t.quantity ELSE 0 END), 0) as quantity,
                            CASE
                                WHEN COALESCE(SUM(CASE WHEN t.side = 'BUY' THEN t.quantity ELSE 0 END), 0) = 0 THEN 0
                                ELSE COALESCE(SUM(CASE WHEN t.side = 'BUY' THEN t.quantity * t.unit_price + COALESCE(t.fee_amount, 0) END), 0) / 
                                     COALESCE(SUM(CASE WHEN t.side = 'BUY' THEN t.quantity ELSE 0 END), 1)
                            END as average_cost
                        FROM instrument i
                        LEFT JOIN trade_transaction t ON t.instrument_id = i.id
                            AND t.portfolio_id = ?
                            AND DATE(t.executed_at) <= ?
                        WHERE i.is_active = TRUE
                            AND EXISTS (
                                SELECT 1 FROM trade_transaction t2
                                WHERE t2.portfolio_id = ? AND t2.instrument_id = i.id
                            )
                        GROUP BY i.id
                        HAVING quantity > 0
                    ) hist
                    LEFT JOIN (
                        SELECT mp.instrument_id, mp.close_price
                        FROM market_price mp
                        INNER JOIN (
                            SELECT instrument_id, MAX(price_date) as latest_date
                            FROM market_price
                            WHERE price_date <= ? AND instrument_id IN (
                                SELECT DISTINCT instrument_id FROM trade_transaction WHERE portfolio_id = ?
                            )
                            GROUP BY instrument_id
                        ) latest ON mp.instrument_id = latest.instrument_id AND mp.price_date = latest.latest_date
                    ) mp ON mp.instrument_id = hist.id
                    ON DUPLICATE KEY UPDATE
                        priced_market_value = VALUES(priced_market_value),
                        total_cost_basis = VALUES(total_cost_basis),
                        priced_cost_basis = VALUES(priced_cost_basis),
                        unrealized_pnl = VALUES(unrealized_pnl),
                        priced_position_count = VALUES(priced_position_count),
                        unpriced_position_count = VALUES(unpriced_position_count),
                        calculated_at = CURRENT_TIMESTAMP(6)
                    """,
                    portfolioId,
                    valuationDate,
                    portfolioId,
                    valuationDate,
                    portfolioId,
                    valuationDate,
                    portfolioId);
        } catch (Exception e) {
            // Skip if this date's snapshot cannot be calculated
        }
    }

    private record PositionState(
            BigDecimal quantity,
            BigDecimal averageCost,
            BigDecimal realizedPnl) {}
}
