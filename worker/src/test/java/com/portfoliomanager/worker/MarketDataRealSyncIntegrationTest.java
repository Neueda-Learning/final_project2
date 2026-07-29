package com.portfoliomanager.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@EnabledIfEnvironmentVariable(
        named = "RUN_REAL_MARKET_SYNC",
        matches = "true")
@SpringBootTest(properties = {
    "market-data.provider=alpaca",
    "market-data.sync-cron=0 0 0 1 1 *",
    "market-data.manual-poll-interval-ms=3600000"
})
class MarketDataRealSyncIntegrationTest {

    @Autowired
    private MarketDataSyncJob job;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void synchronizesTheRealPortfolioUniverseWithAlpaca() {
        Long previousRunCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM market_data_sync_run", Long.class);

        job.scheduledSync();

        Map<String, Object> run = jdbc.queryForMap("""
                SELECT provider, status, requested_count, success_count, failure_count
                FROM market_data_sync_run
                ORDER BY started_at DESC
                LIMIT 1
                """);
        Long currentRunCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM market_data_sync_run", Long.class);

        assertThat(currentRunCount).isEqualTo(previousRunCount + 1);
        assertThat(run.get("provider")).isEqualTo("alpaca");
        assertThat(run.get("status")).isIn("SUCCEEDED", "PARTIAL");
        assertThat(((Number) run.get("requested_count")).intValue()).isPositive();
        assertThat(((Number) run.get("success_count")).intValue()).isPositive();
    }
}
