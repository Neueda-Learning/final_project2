package com.portfoliomanager.worker.provider;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "market-data.provider", havingValue = "fixture")
public class FixtureMarketDataProvider implements MarketDataProvider {

    @Override
    public String name() {
        return "fixture";
    }

    @Override
    public List<InstrumentSearchResult> searchInstruments(String query, int limit) {
        return List.of(
                        new InstrumentSearchResult(
                                "AAPL", "Apple Inc.", "NASDAQ", "USD", "Common Stock"),
                        new InstrumentSearchResult(
                                "SPY", "SPDR S&P 500 ETF Trust", "NYSE ARCA", "USD", "ETF"))
                .stream()
                .filter(item -> item.symbol().contains(query.toUpperCase())
                        || item.name().toUpperCase().contains(query.toUpperCase()))
                .limit(Math.max(0, limit))
                .toList();
    }

    @Override
    public List<DailyPrice> fetchDailyCloses(
            List<String> symbols, LocalDate start, LocalDate end) {
        LocalDate priceDate = previousOrSameWeekday(end.minusDays(1));
        if (priceDate.isBefore(start)) {
            return List.of();
        }
        return symbols.stream()
                .map(String::toUpperCase)
                .map(symbol -> fixturePrice(symbol, priceDate))
                .toList();
    }

    @Override
    public List<IntradayBar> fetchIntradayBars(
            String symbol,
            String interval,
            LocalDateTime start,
            LocalDateTime end) {
        LocalDateTime timestamp = end.minusMinutes(1).withSecond(0).withNano(0);
        BigDecimal close = fixturePrice(symbol, timestamp.toLocalDate()).closePrice();
        return List.of(new IntradayBar(
                symbol,
                interval,
                timestamp,
                close,
                close,
                close,
                close,
                1_000L,
                "USD",
                name()));
    }

    @Override
    public boolean healthCheck() {
        return true;
    }

    private DailyPrice fixturePrice(String symbol, LocalDate priceDate) {
        BigDecimal close = BigDecimal.valueOf(75 + Math.floorMod(symbol.hashCode(), 200))
                .setScale(8);
        return new DailyPrice(
                symbol,
                priceDate,
                close.subtract(new BigDecimal("1.25000000")),
                close.add(new BigDecimal("2.00000000")),
                close.subtract(new BigDecimal("2.00000000")),
                close,
                close,
                1_000_000L + Math.floorMod(symbol.hashCode(), 1_000_000),
                "USD",
                name(),
                null);
    }

    private LocalDate previousOrSameWeekday(LocalDate date) {
        LocalDate result = date;
        while (result.getDayOfWeek() == DayOfWeek.SATURDAY
                || result.getDayOfWeek() == DayOfWeek.SUNDAY) {
            result = result.minusDays(1);
        }
        return result;
    }
}
