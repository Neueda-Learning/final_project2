package com.portfoliomanager.worker.provider;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface MarketDataProvider {

    String name();

    List<InstrumentSearchResult> searchInstruments(String query, int limit);

    List<DailyPrice> fetchDailyCloses(
            List<String> symbols,
            LocalDate start,
            LocalDate end);

    List<IntradayBar> fetchIntradayBars(
            String symbol,
            String interval,
            LocalDateTime start,
            LocalDateTime end);

    boolean healthCheck();
}
