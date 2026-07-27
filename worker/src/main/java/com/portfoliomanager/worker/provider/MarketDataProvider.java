package com.portfoliomanager.worker.provider;

import java.time.LocalDate;
import java.util.List;

public interface MarketDataProvider {

    String name();

    List<DailyPrice> getDailyPrices(
            List<String> symbols,
            LocalDate start,
            LocalDate end);
}
