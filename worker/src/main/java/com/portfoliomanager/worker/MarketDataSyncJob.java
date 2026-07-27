package com.portfoliomanager.worker;

import com.portfoliomanager.worker.provider.MarketDataProvider;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MarketDataSyncJob {

    private static final Logger log = LoggerFactory.getLogger(MarketDataSyncJob.class);
    private static final String LOCK_NAME = "portfolio_manager_market_sync";

    private final MarketDataProvider provider;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public MarketDataSyncJob(MarketDataProvider provider, JdbcTemplate jdbc) {
        this(provider, jdbc, Clock.systemUTC());
    }

    MarketDataSyncJob(MarketDataProvider provider, JdbcTemplate jdbc, Clock clock) {
        this.provider = provider;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Scheduled(cron = "${market-data.sync-cron}", zone = "UTC")
    public void synchronize() {
        Integer acquired = jdbc.queryForObject("SELECT GET_LOCK(?, 0)", Integer.class, LOCK_NAME);
        if (acquired == null || acquired != 1) {
            log.info("Market-data sync skipped because another worker owns the lock");
            return;
        }
        try {
            var today = LocalDate.now(clock);
            var prices = provider.getDailyPrices(List.of(), today.minusDays(7), today.plusDays(1));
            log.info("Normalized {} daily prices from {}", prices.size(), provider.name());
        } finally {
            jdbc.queryForObject("SELECT RELEASE_LOCK(?)", Integer.class, LOCK_NAME);
        }
    }
}
