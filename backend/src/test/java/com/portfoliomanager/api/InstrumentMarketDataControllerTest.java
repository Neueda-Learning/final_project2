package com.portfoliomanager.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.portfoliomanager.api.ApiModels.MarketBarPageResponse;
import com.portfoliomanager.api.ApiModels.MarketBarResponse;
import com.portfoliomanager.service.MarketDataService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InstrumentMarketDataControllerTest {

    private MarketDataService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = Mockito.mock(MarketDataService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new InstrumentMarketDataController(service))
                .build();
    }

    @Test
    void returnsPaginatedMinuteBarsWithPrivateCacheHeader() throws Exception {
        LocalDateTime from = LocalDateTime.of(2026, 7, 27, 13, 30);
        LocalDateTime to = LocalDateTime.of(2026, 7, 27, 20, 0);
        var bar = new MarketBarResponse(
                "instrument-1",
                "AAPL",
                "1min",
                LocalDateTime.of(2026, 7, 27, 19, 59),
                new BigDecimal("214.10"),
                new BigDecimal("214.30"),
                new BigDecimal("214.00"),
                new BigDecimal("214.25"),
                1_200L,
                "USD",
                "twelve-data");
        when(service.bars(
                        eq("instrument-1"),
                        eq("1min"),
                        eq(from),
                        eq(to),
                        eq(1),
                        eq(100)))
                .thenReturn(new MarketBarPageResponse(
                        List.of(bar), 1, 100, 390, true));

        mockMvc.perform(get("/api/v1/instruments/instrument-1/bars")
                        .param("interval", "1min")
                        .param("from", "2026-07-27T13:30:00")
                        .param("to", "2026-07-27T20:00:00")
                        .param("page", "1")
                        .param("pageSize", "100"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Cache-Control", "max-age=15, private"))
                .andExpect(jsonPath("$.items[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$.items[0].interval").value("1min"))
                .andExpect(jsonPath("$.items[0].close").value("214.25"))
                .andExpect(jsonPath("$.total").value(390))
                .andExpect(jsonPath("$.hasNext").value(true));
    }
}
