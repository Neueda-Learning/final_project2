package com.portfoliomanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.portfoliomanager.api.ApiModels.MarketBarResponse;
import com.portfoliomanager.api.ApiModels.TransactionCreateRequest;
import com.portfoliomanager.domain.TradeSide;
import com.portfoliomanager.domain.model.Instrument;
import com.portfoliomanager.domain.model.Portfolio;
import com.portfoliomanager.domain.model.TradeTransaction;
import com.portfoliomanager.repository.InstrumentRepository;
import com.portfoliomanager.repository.PortfolioPositionRepository;
import com.portfoliomanager.repository.PortfolioRepository;
import com.portfoliomanager.repository.TradeTransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TradingServiceTest {

    @Mock private InstrumentRepository instruments;
    @Mock private PortfolioRepository portfolios;
    @Mock private TradeTransactionRepository transactions;
    @Mock private PortfolioPositionRepository positions;
    @Mock private MarketDataService marketData;
    @Mock private Portfolio portfolio;
    @Mock private Instrument instrument;

    private TradingService service;

    @BeforeEach
    void setUp() {
        service = new TradingService(instruments, portfolios, transactions, positions, marketData);
    }

    @Test
    void blankInstrumentQueryListsTheControlledActiveUniverse() {
        given(instruments.findByActiveTrueOrderBySymbol()).willReturn(List.of());

        assertThat(service.searchInstruments(null, 50)).isEmpty();

        verify(instruments).findByActiveTrueOrderBySymbol();
    }

    @Test
    void createTransactionUsesTheSelectedStoredMinuteClose() {
        String portfolioId = "portfolio-id";
        String instrumentId = "instrument-id";
        LocalDateTime executionTimestamp = LocalDateTime.of(2026, 7, 27, 19, 59);
        given(portfolios.findById(portfolioId)).willReturn(Optional.of(portfolio));
        given(portfolio.isArchived()).willReturn(false);
        given(instruments.findById(instrumentId)).willReturn(Optional.of(instrument));
        given(instrument.isActive()).willReturn(true);
        given(transactions.findByPortfolioIdAndIdempotencyKey(portfolioId, "key"))
                .willReturn(Optional.empty());
        given(positions.findByPortfolioAndInstrumentForUpdate(portfolioId, instrumentId))
                .willReturn(Optional.empty());
        given(marketData.tradableBar(instrumentId, executionTimestamp))
                .willReturn(new MarketBarResponse(
                        instrumentId,
                        "AAPL",
                        "1min",
                        executionTimestamp,
                        new BigDecimal("213.90"),
                        new BigDecimal("214.10"),
                        new BigDecimal("213.80"),
                        new BigDecimal("214.05000000"),
                        1200L,
                        "USD",
                        "twelve-data"));

        service.createTransaction(
                portfolioId,
                "key",
                new TransactionCreateRequest(
                        instrumentId,
                        TradeSide.BUY,
                        new BigDecimal("2"),
                        executionTimestamp,
                        null,
                        null));

        ArgumentCaptor<TradeTransaction> transactionCaptor =
                ArgumentCaptor.forClass(TradeTransaction.class);
        verify(transactions).save(transactionCaptor.capture());
        TradeTransaction saved = transactionCaptor.getValue();
        assertThat(saved.getUnitPrice()).isEqualByComparingTo("214.05000000");
        assertThat(saved.getCurrency()).isEqualTo("USD");
        assertThat(saved.getExecutedAt()).isEqualTo(executionTimestamp);
        assertThat(saved.getFeeAmount()).isEqualByComparingTo("0");
    }
}
