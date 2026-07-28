package com.portfoliomanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.portfoliomanager.api.ApiModels.MarketPriceResponse;
import com.portfoliomanager.api.ApiModels.TransactionCreateRequest;
import com.portfoliomanager.domain.PriceStatus;
import com.portfoliomanager.domain.TradeSide;
import com.portfoliomanager.domain.model.Instrument;
import com.portfoliomanager.domain.model.Portfolio;
import com.portfoliomanager.domain.model.TradeTransaction;
import com.portfoliomanager.repository.InstrumentRepository;
import com.portfoliomanager.repository.PortfolioPositionRepository;
import com.portfoliomanager.repository.PortfolioRepository;
import com.portfoliomanager.repository.TradeTransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
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
    void createTransactionUsesStoredDailyCloseInsteadOfClientSuppliedPrice() {
        String portfolioId = "portfolio-id";
        String instrumentId = "instrument-id";
        LocalDate priceDate = LocalDate.of(2026, 7, 27);
        given(portfolios.findById(portfolioId)).willReturn(Optional.of(portfolio));
        given(portfolio.isArchived()).willReturn(false);
        given(instruments.findById(instrumentId)).willReturn(Optional.of(instrument));
        given(instrument.isActive()).willReturn(true);
        given(transactions.findByPortfolioIdAndIdempotencyKey(portfolioId, "key"))
                .willReturn(Optional.empty());
        given(positions.findByPortfolioAndInstrumentForUpdate(portfolioId, instrumentId))
                .willReturn(Optional.empty());
        given(marketData.tradablePrice(instrumentId, priceDate))
                .willReturn(new MarketPriceResponse(
                        instrumentId,
                        "AAPL",
                        priceDate,
                        new BigDecimal("214.05000000"),
                        new BigDecimal("214.05000000"),
                        "USD",
                        "twelve-data",
                        LocalDateTime.of(2026, 7, 27, 16, 0),
                        LocalDateTime.of(2026, 7, 28, 1, 0),
                        PriceStatus.FRESH));

        service.createTransaction(
                portfolioId,
                "key",
                new TransactionCreateRequest(
                        instrumentId,
                        TradeSide.BUY,
                        new BigDecimal("2"),
                        priceDate,
                        null,
                        null));

        ArgumentCaptor<TradeTransaction> transactionCaptor =
                ArgumentCaptor.forClass(TradeTransaction.class);
        verify(transactions).save(transactionCaptor.capture());
        TradeTransaction saved = transactionCaptor.getValue();
        assertThat(saved.getUnitPrice()).isEqualByComparingTo("214.05000000");
        assertThat(saved.getCurrency()).isEqualTo("USD");
        assertThat(saved.getExecutedAt()).isEqualTo(LocalDateTime.of(2026, 7, 27, 16, 0));
        assertThat(saved.getFeeAmount()).isEqualByComparingTo("0");
    }
}
