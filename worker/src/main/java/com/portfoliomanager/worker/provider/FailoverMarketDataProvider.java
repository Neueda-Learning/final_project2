package com.portfoliomanager.worker.provider;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FailoverMarketDataProvider implements MarketDataProvider {

    private static final Logger log =
            LoggerFactory.getLogger(FailoverMarketDataProvider.class);

    private final MarketDataProvider primary;
    private final MarketDataProvider fallback;

    public FailoverMarketDataProvider(
            MarketDataProvider primary, MarketDataProvider fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public String name() {
        return primary.name();
    }

    @Override
    public List<InstrumentSearchResult> searchInstruments(String query, int limit) {
        if (!primary.healthCheck()) {
            return fallback.searchInstruments(query, limit);
        }
        try {
            return primary.searchInstruments(query, limit);
        } catch (RuntimeException primaryFailure) {
            return fallbackAfterFailure(
                    "instrument search",
                    primaryFailure,
                    () -> fallback.searchInstruments(query, limit));
        }
    }

    @Override
    public List<DailyPrice> fetchDailyCloses(
            List<String> symbols, LocalDate start, LocalDate end) {
        if (!primary.healthCheck()) {
            return fetchFallbackDailyCloses(symbols, start, end);
        }

        List<DailyPrice> primaryPrices;
        try {
            primaryPrices = primary.fetchDailyCloses(symbols, start, end);
        } catch (RuntimeException primaryFailure) {
            return fallbackAfterFailure(
                    "daily bars",
                    primaryFailure,
                    () -> fetchFallbackDailyCloses(symbols, start, end));
        }

        Set<String> returnedSymbols = new HashSet<>();
        primaryPrices.forEach(price ->
                returnedSymbols.add(normalizeSymbol(price.symbol())));
        List<String> missingSymbols = symbols.stream()
                .filter(symbol -> !returnedSymbols.contains(normalizeSymbol(symbol)))
                .toList();
        if (missingSymbols.isEmpty() || !fallback.healthCheck()) {
            return primaryPrices;
        }

        List<DailyPrice> combined = new ArrayList<>(primaryPrices);
        for (String missingSymbol : missingSymbols) {
            try {
                combined.addAll(
                        fallback.fetchDailyCloses(List.of(missingSymbol), start, end));
            } catch (RuntimeException fallbackFailure) {
                log.warn(
                        "{} fallback failed for daily bars on {}: {}",
                        fallback.name(),
                        missingSymbol,
                        rootMessage(fallbackFailure));
            }
        }
        return combined;
    }

    private List<DailyPrice> fetchFallbackDailyCloses(
            List<String> symbols, LocalDate start, LocalDate end) {
        List<DailyPrice> prices = new ArrayList<>();
        for (String symbol : symbols) {
            prices.addAll(fallback.fetchDailyCloses(List.of(symbol), start, end));
        }
        return prices;
    }

    @Override
    public List<IntradayBar> fetchIntradayBars(
            String symbol,
            String interval,
            LocalDateTime start,
            LocalDateTime end) {
        if (!primary.healthCheck()) {
            return fallback.fetchIntradayBars(symbol, interval, start, end);
        }
        try {
            List<IntradayBar> bars =
                    primary.fetchIntradayBars(symbol, interval, start, end);
            if (!bars.isEmpty() || !fallback.healthCheck()) {
                return bars;
            }
            log.warn(
                    "{} returned no intraday bars for {}; trying {}",
                    primary.name(),
                    symbol,
                    fallback.name());
            return fallback.fetchIntradayBars(symbol, interval, start, end);
        } catch (RuntimeException primaryFailure) {
            return fallbackAfterFailure(
                    "intraday bars for " + symbol,
                    primaryFailure,
                    () -> fallback.fetchIntradayBars(symbol, interval, start, end));
        }
    }

    @Override
    public boolean healthCheck() {
        return primary.healthCheck() || fallback.healthCheck();
    }

    private <T> T fallbackAfterFailure(
            String operation,
            RuntimeException primaryFailure,
            ProviderCall<T> fallbackCall) {
        if (!fallback.healthCheck()) {
            throw primaryFailure;
        }
        log.warn(
                "{} failed for {}; trying {}: {}",
                primary.name(),
                operation,
                fallback.name(),
                rootMessage(primaryFailure));
        try {
            return fallbackCall.call();
        } catch (RuntimeException fallbackFailure) {
            fallbackFailure.addSuppressed(primaryFailure);
            throw fallbackFailure;
        }
    }

    private String normalizeSymbol(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }

    @FunctionalInterface
    private interface ProviderCall<T> {
        T call();
    }
}
