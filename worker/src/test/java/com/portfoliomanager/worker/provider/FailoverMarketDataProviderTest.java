package com.portfoliomanager.worker.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class FailoverMarketDataProviderTest {

    private static final LocalDate START = LocalDate.of(2026, 7, 1);
    private static final LocalDate END = LocalDate.of(2026, 7, 29);

    @Test
    void fillsOnlyMissingDailySymbolsFromFallback() {
        MarketDataProvider primary = mock(MarketDataProvider.class);
        MarketDataProvider fallback = mock(MarketDataProvider.class);
        when(primary.name()).thenReturn("alpaca");
        when(fallback.name()).thenReturn("twelve-data");
        when(primary.healthCheck()).thenReturn(true);
        when(fallback.healthCheck()).thenReturn(true);
        when(primary.fetchDailyCloses(List.of("AAPL", "SPY"), START, END))
                .thenReturn(List.of(price("AAPL", "alpaca")));
        when(fallback.fetchDailyCloses(List.of("SPY"), START, END))
                .thenReturn(List.of(price("SPY", "twelve-data")));

        var provider = new FailoverMarketDataProvider(primary, fallback);

        assertThat(provider.fetchDailyCloses(
                        List.of("AAPL", "SPY"), START, END))
                .extracting(DailyPrice::source)
                .containsExactly("alpaca", "twelve-data");
        verify(fallback).fetchDailyCloses(List.of("SPY"), START, END);
    }

    @Test
    void splitsFullDailyFallbackIntoSingleSymbolRequests() {
        MarketDataProvider primary = mock(MarketDataProvider.class);
        MarketDataProvider fallback = mock(MarketDataProvider.class);
        when(primary.name()).thenReturn("alpaca");
        when(fallback.name()).thenReturn("twelve-data");
        when(primary.healthCheck()).thenReturn(true);
        when(fallback.healthCheck()).thenReturn(true);
        when(primary.fetchDailyCloses(List.of("AAPL", "SPY"), START, END))
                .thenThrow(new MarketDataProviderException("unavailable"));
        when(fallback.fetchDailyCloses(List.of("AAPL"), START, END))
                .thenReturn(List.of(price("AAPL", "twelve-data")));
        when(fallback.fetchDailyCloses(List.of("SPY"), START, END))
                .thenReturn(List.of(price("SPY", "twelve-data")));

        var provider = new FailoverMarketDataProvider(primary, fallback);

        assertThat(provider.fetchDailyCloses(
                        List.of("AAPL", "SPY"), START, END))
                .extracting(DailyPrice::symbol)
                .containsExactly("AAPL", "SPY");
        verify(fallback).fetchDailyCloses(List.of("AAPL"), START, END);
        verify(fallback).fetchDailyCloses(List.of("SPY"), START, END);
    }

    @Test
    void usesFallbackWhenPrimaryIntradayResponseIsEmpty() {
        MarketDataProvider primary = mock(MarketDataProvider.class);
        MarketDataProvider fallback = mock(MarketDataProvider.class);
        LocalDateTime start = LocalDateTime.of(2026, 7, 28, 14, 0);
        LocalDateTime end = LocalDateTime.of(2026, 7, 28, 20, 0);
        IntradayBar fallbackBar = new IntradayBar(
                "AAPL",
                "1min",
                end.minusMinutes(1),
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                10L,
                "USD",
                "twelve-data");
        when(primary.name()).thenReturn("alpaca");
        when(fallback.name()).thenReturn("twelve-data");
        when(primary.healthCheck()).thenReturn(true);
        when(fallback.healthCheck()).thenReturn(true);
        when(primary.fetchIntradayBars("AAPL", "1min", start, end))
                .thenReturn(List.of());
        when(fallback.fetchIntradayBars("AAPL", "1min", start, end))
                .thenReturn(List.of(fallbackBar));

        var provider = new FailoverMarketDataProvider(primary, fallback);

        assertThat(provider.fetchIntradayBars("AAPL", "1min", start, end))
                .containsExactly(fallbackBar);
        verify(fallback).fetchIntradayBars("AAPL", "1min", start, end);
    }

    private DailyPrice price(String symbol, String source) {
        return new DailyPrice(
                symbol,
                LocalDate.of(2026, 7, 28),
                new BigDecimal("100"),
                new BigDecimal("102"),
                new BigDecimal("99"),
                new BigDecimal("101"),
                new BigDecimal("101"),
                1_000L,
                "USD",
                source,
                null);
    }
}
