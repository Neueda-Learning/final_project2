package com.portfoliomanager.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.portfoliomanager.service.MarketDataService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@EnabledIfEnvironmentVariable(
        named = "RUN_REAL_MARKET_API",
        matches = "true")
@SpringBootTest
@Transactional
@Rollback
class MarketDataRealApiIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MarketDataService marketDataService;

    private MockMvc mockMvc;
    private String instrumentId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        instrumentId = jdbc.queryForObject(
                "SELECT id FROM instrument WHERE symbol = 'AAPL'",
                String.class);
    }

    @Test
    void exercisesEveryMarketDataHttpAndTradingLookupInterface()
            throws Exception {
        mockMvc.perform(get("/api/v1/market-data/sync-runs/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("alpaca"))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.successCount").value(39))
                .andExpect(jsonPath("$.failureCount").value(0));

        mockMvc.perform(get("/api/v1/instruments")
                        .param("query", "AAPL")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$.items[0].isActive").value(true));

        mockMvc.perform(get(
                        "/api/v1/instruments/{instrumentId}/latest-price",
                        instrumentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.source").value("alpaca"))
                .andExpect(jsonPath("$.closePrice").isNotEmpty());

        mockMvc.perform(get(
                                "/api/v1/instruments/{instrumentId}/tradable-prices",
                                instrumentId)
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10))
                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$[0].source").value("alpaca"));

        LocalDateTime latestAlpacaBar = jdbc.queryForObject(
                """
                SELECT MAX(bar_timestamp)
                FROM market_intraday_bar
                WHERE instrument_id = ? AND source = 'alpaca'
                """,
                LocalDateTime.class,
                instrumentId);
        LocalDateTime from = latestAlpacaBar.minusHours(2);
        LocalDateTime to = latestAlpacaBar.plusMinutes(1);
        long expectedUniqueBars = jdbc.queryForObject(
                """
                SELECT COUNT(DISTINCT bar_timestamp)
                FROM market_intraday_bar
                WHERE instrument_id = ?
                  AND interval_code = '1min'
                  AND bar_timestamp >= ?
                  AND bar_timestamp < ?
                """,
                Long.class,
                instrumentId,
                from,
                to);

        MvcResult barsResult = mockMvc.perform(get(
                                "/api/v1/instruments/{instrumentId}/bars",
                                instrumentId)
                        .param("interval", "1min")
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .param("page", "1")
                        .param("pageSize", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(expectedUniqueBars))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.pageSize").value(500))
                .andReturn();

        String barsJson = barsResult.getResponse().getContentAsString();
        assertThat(barsJson).contains("\"source\":\"alpaca\"");

        LocalDate latestAlpacaDate = jdbc.queryForObject(
                """
                SELECT MAX(price_date)
                FROM market_price
                WHERE instrument_id = ? AND source = 'alpaca'
                """,
                LocalDate.class,
                instrumentId);
        assertThat(marketDataService.tradablePrice(
                                instrumentId, latestAlpacaDate)
                        .source())
                .isEqualTo("alpaca");
        assertThat(marketDataService.tradableBar(
                                instrumentId, latestAlpacaBar)
                        .source())
                .isEqualTo("alpaca");

        mockMvc.perform(post("/api/v1/market-data/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"force\":false}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.provider").value("alpaca"))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.triggeredBy").value("MANUAL"));
    }

    @Test
    void rejectsInvalidMarketDataRequests() throws Exception {
        mockMvc.perform(get(
                        "/api/v1/instruments/{instrumentId}/latest-price",
                        "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(
                        "/api/v1/instruments/{instrumentId}/tradable-prices",
                                instrumentId)
                        .param("limit", "251"))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(get(
                                "/api/v1/instruments/{instrumentId}/bars",
                                instrumentId)
                        .param("interval", "2min"))
                .andExpect(status().isUnprocessableEntity());

        LocalDateTime now = LocalDateTime.now();
        mockMvc.perform(get(
                                "/api/v1/instruments/{instrumentId}/bars",
                                instrumentId)
                        .param("from", now.toString())
                        .param("to", now.toString()))
                .andExpect(status().isUnprocessableEntity());
    }
}
