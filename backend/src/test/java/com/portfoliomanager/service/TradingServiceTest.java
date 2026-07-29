package com.portfoliomanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.portfoliomanager.api.ApiModels.TransactionCreateRequest;
import com.portfoliomanager.domain.AssetType;
import com.portfoliomanager.domain.TradeSide;
import com.portfoliomanager.domain.model.Instrument;
import com.portfoliomanager.domain.model.Portfolio;
import com.portfoliomanager.domain.model.PortfolioPosition;
import com.portfoliomanager.domain.model.TradeTransaction;
import com.portfoliomanager.repository.InstrumentRepository;
import com.portfoliomanager.repository.PortfolioPositionRepository;
import com.portfoliomanager.repository.PortfolioRepository;
import com.portfoliomanager.repository.TradeTransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class TradingServiceTest {

    @Mock private InstrumentRepository instruments;
    @Mock private PortfolioRepository portfolios;
    @Mock private TradeTransactionRepository transactions;
    @Mock private PortfolioPositionRepository positions;
    @Mock private JdbcTemplate jdbc;
    @Mock private Portfolio portfolio;
    @Mock private Instrument instrument;
    @Mock private PortfolioPosition position;
    @Mock private TradeTransaction transaction;

    private TradingService service;

    @BeforeEach
    void setUp() {
        service = new TradingService(instruments, portfolios, transactions, positions, jdbc);
    }

    @Test
    void blankInstrumentQueryListsTheControlledActiveUniverse() {
        given(instruments.findByActiveTrueOrderBySymbol()).willReturn(List.of());

        assertThat(service.searchInstruments(null, 50)).isEmpty();

        verify(instruments).findByActiveTrueOrderBySymbol();
    }

    @Test
    void createTransactionUsesTheManuallyEnteredPriceAndTradeDate() {
        String portfolioId = "portfolio-id";
        String instrumentId = "instrument-id";
        LocalDate tradeDate = LocalDate.of(2026, 7, 27);
        given(portfolios.findById(portfolioId)).willReturn(Optional.of(portfolio));
        given(portfolio.isArchived()).willReturn(false);
        given(instruments.findById(instrumentId)).willReturn(Optional.of(instrument));
        given(instrument.isActive()).willReturn(true);
        given(instrument.getCurrency()).willReturn("USD");
        given(transactions.findByPortfolioIdAndIdempotencyKey(portfolioId, "key"))
                .willReturn(Optional.empty());
        given(transactions.findHistoryByPortfolioIdAndInstrumentId(portfolioId, instrumentId))
            .willReturn(List.of());
        given(positions.findByPortfolioAndInstrumentForUpdate(portfolioId, instrumentId))
                .willReturn(Optional.empty());
        service.createTransaction(
                portfolioId,
                "key",
                new TransactionCreateRequest(
                        instrumentId,
                        TradeSide.BUY,
                        new BigDecimal("2"),
                        tradeDate,
                        new BigDecimal("214.05000000"),
                        null,
                        null));

        ArgumentCaptor<TradeTransaction> transactionCaptor =
                ArgumentCaptor.forClass(TradeTransaction.class);
        verify(transactions).save(transactionCaptor.capture());
        TradeTransaction saved = transactionCaptor.getValue();
        assertThat(saved.getUnitPrice()).isEqualByComparingTo("214.05000000");
        assertThat(saved.getCurrency()).isEqualTo("USD");
        assertThat(saved.getExecutedAt()).isEqualTo(tradeDate.atTime(16, 0));
        assertThat(saved.getFeeAmount()).isEqualByComparingTo("0");
    }

        @Test
        void sellWithoutAnyOwnedSharesIsRejected() {
        String portfolioId = "portfolio-id";
        String instrumentId = "instrument-id";
        given(portfolios.findById(portfolioId)).willReturn(Optional.of(portfolio));
        given(portfolio.isArchived()).willReturn(false);
        given(instruments.findById(instrumentId)).willReturn(Optional.of(instrument));
        given(instrument.isActive()).willReturn(true);
        given(transactions.findByPortfolioIdAndIdempotencyKey(portfolioId, "key"))
            .willReturn(Optional.empty());
        given(transactions.findHistoryByPortfolioIdAndInstrumentId(portfolioId, instrumentId))
            .willReturn(List.of());
        given(positions.findByPortfolioAndInstrumentForUpdate(portfolioId, instrumentId))
            .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.createTransaction(
            portfolioId,
            "key",
            new TransactionCreateRequest(
                instrumentId,
                TradeSide.SELL,
                new BigDecimal("1"),
                LocalDate.of(2026, 7, 27),
                new BigDecimal("214.05000000"),
                null,
                null)))
            .isInstanceOf(ConflictException.class)
            .hasMessage("INSUFFICIENT_QUANTITY");

        verify(transactions, never()).save(any());
        }

        @Test
        void sellMoreThanOwnedSharesIsRejected() {
        String portfolioId = "portfolio-id";
        String instrumentId = "instrument-id";
        given(portfolios.findById(portfolioId)).willReturn(Optional.of(portfolio));
        given(portfolio.isArchived()).willReturn(false);
        given(instruments.findById(instrumentId)).willReturn(Optional.of(instrument));
        given(instrument.isActive()).willReturn(true);
        given(instrument.getCurrency()).willReturn("USD");
        given(transactions.findByPortfolioIdAndIdempotencyKey(portfolioId, "key"))
            .willReturn(Optional.empty());
        given(transactions.findHistoryByPortfolioIdAndInstrumentId(portfolioId, instrumentId))
            .willReturn(List.of(existingTrade(
                "buy-1",
                TradeSide.BUY,
                new BigDecimal("10"),
                new BigDecimal("100.00000000"),
                LocalDate.of(2026, 7, 22))));
        given(positions.findByPortfolioAndInstrumentForUpdate(portfolioId, instrumentId))
            .willReturn(Optional.of(position));

        assertThatThrownBy(() -> service.createTransaction(
            portfolioId,
            "key",
            new TransactionCreateRequest(
                instrumentId,
                TradeSide.SELL,
                new BigDecimal("15"),
                LocalDate.of(2026, 7, 23),
                new BigDecimal("214.05000000"),
                null,
                null)))
            .isInstanceOf(ConflictException.class)
            .hasMessage("INSUFFICIENT_QUANTITY");

        verify(transactions, never()).save(any());
        }

        @Test
        void backdatedSellBeforeTheFirstBuyIsRejected() {
        String portfolioId = "portfolio-id";
        String instrumentId = "instrument-id";
        given(portfolios.findById(portfolioId)).willReturn(Optional.of(portfolio));
        given(portfolio.isArchived()).willReturn(false);
        given(instruments.findById(instrumentId)).willReturn(Optional.of(instrument));
        given(instrument.isActive()).willReturn(true);
        given(instrument.getCurrency()).willReturn("USD");
        given(transactions.findByPortfolioIdAndIdempotencyKey(portfolioId, "key"))
            .willReturn(Optional.empty());
        given(transactions.findHistoryByPortfolioIdAndInstrumentId(portfolioId, instrumentId))
            .willReturn(List.of(existingTrade(
                "buy-1",
                TradeSide.BUY,
                new BigDecimal("10"),
                new BigDecimal("100.00000000"),
                LocalDate.of(2026, 7, 22))));
        given(positions.findByPortfolioAndInstrumentForUpdate(portfolioId, instrumentId))
            .willReturn(Optional.of(position));

        assertThatThrownBy(() -> service.createTransaction(
            portfolioId,
            "key",
            new TransactionCreateRequest(
                instrumentId,
                TradeSide.SELL,
                new BigDecimal("5"),
                LocalDate.of(2026, 7, 15),
                new BigDecimal("214.05000000"),
                null,
                null)))
            .isInstanceOf(ConflictException.class)
            .hasMessage("INSUFFICIENT_QUANTITY");

        verify(transactions, never()).save(any());
        }

    @Test
    void populatedPositionsSkipTheSeparatePortfolioExistenceQuery() {
        String portfolioId = "portfolio-id";
        given(positions.findByPortfolioId(portfolioId)).willReturn(List.of(position));
        given(position.getInstrument()).willReturn(instrument);
        given(instrument.getId()).willReturn("instrument-id");
        given(instrument.getSymbol()).willReturn("AAPL");
        given(instrument.getName()).willReturn("Apple Inc.");
        given(instrument.getAssetType()).willReturn(AssetType.STOCK);

        assertThat(service.listPositions(portfolioId)).hasSize(1);

        verify(portfolios, never()).existsById(portfolioId);
    }

    @Test
    void emptyPositionsStillDistinguishAMissingPortfolio() {
        String portfolioId = "missing-portfolio";
        given(positions.findByPortfolioId(portfolioId)).willReturn(List.of());
        given(portfolios.existsById(portfolioId)).willReturn(false);

        assertThatThrownBy(() -> service.listPositions(portfolioId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Portfolio not found: " + portfolioId);
    }

    @Test
    void populatedTransactionPageSkipsTheSeparatePortfolioExistenceQuery() {
        String portfolioId = "portfolio-id";
        PageRequest pageable = PageRequest.of(0, 20);
        given(transactions.findByPortfolioIdOrderByExecutedAtDesc(portfolioId, pageable))
                .willReturn(new PageImpl<>(List.of(transaction), pageable, 1));
        given(transaction.getPortfolio()).willReturn(portfolio);
        given(portfolio.getId()).willReturn(portfolioId);
        given(transaction.getInstrument()).willReturn(instrument);
        given(instrument.getId()).willReturn("instrument-id");
        given(instrument.getSymbol()).willReturn("AAPL");

        assertThat(service.listTransactions(portfolioId, 1, 20).items()).hasSize(1);

        verify(portfolios, never()).existsById(portfolioId);
    }

    @Test
    void emptyTransactionPageStillDistinguishesAMissingPortfolio() {
        String portfolioId = "missing-portfolio";
        PageRequest pageable = PageRequest.of(0, 20);
        given(transactions.findByPortfolioIdOrderByExecutedAtDesc(portfolioId, pageable))
                .willReturn(new PageImpl<>(List.of(), pageable, 0));
        given(portfolios.existsById(portfolioId)).willReturn(false);

        assertThatThrownBy(() -> service.listTransactions(portfolioId, 1, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Portfolio not found: " + portfolioId);
    }

    private TradeTransaction existingTrade(
            String id,
            TradeSide side,
            BigDecimal quantity,
            BigDecimal unitPrice,
            LocalDate tradeDate) {
        return new TradeTransaction(
                id,
                portfolio,
                instrument,
                side,
                quantity,
                unitPrice,
                BigDecimal.ZERO,
                "USD",
                tradeDate.atTime(16, 0),
                id + "-key",
                null);
    }
}
