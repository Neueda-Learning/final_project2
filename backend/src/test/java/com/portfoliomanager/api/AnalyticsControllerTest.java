package com.portfoliomanager.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.portfoliomanager.api.ApiModels.AllocationItemResponse;
import com.portfoliomanager.api.ApiModels.DashboardPositionResponse;
import com.portfoliomanager.api.ApiModels.DashboardResponse;
import com.portfoliomanager.api.ApiModels.PerformancePointResponse;
import com.portfoliomanager.api.ApiModels.PerformanceResponse;
import com.portfoliomanager.api.ApiModels.PortfolioInfoResponse;
import com.portfoliomanager.api.ApiModels.PortfolioSummaryResponse;
import com.portfoliomanager.domain.AssetType;
import com.portfoliomanager.domain.PriceStatus;
import com.portfoliomanager.service.AnalyticsService;
import com.portfoliomanager.service.InvalidDateRangeException;
import com.portfoliomanager.service.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AnalyticsControllerTest {

    private static final String PORTFOLIO_ID = "22222222-2222-2222-2222-222222222222";

    private MockMvc mockMvc;

    @Mock
    private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AnalyticsController(analyticsService))
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void dashboard_returnsAggregatedPayload() throws Exception {
        given(analyticsService.dashboard(PORTFOLIO_ID)).willReturn(sampleDashboard());

        mockMvc.perform(get("/api/v1/portfolios/{portfolioId}/dashboard", PORTFOLIO_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portfolio.id").value(PORTFOLIO_ID))
                .andExpect(jsonPath("$.summary.pricedCostBasis").value("110000.00000030"))
                .andExpect(jsonPath("$.positions[0].priceStatus").value("FRESH"))
                .andExpect(jsonPath("$.allocation[0].allocationPct").value("68.10163232"));
    }

    @Test
    void dashboard_unknownPortfolio_returns404Code() throws Exception {
        given(analyticsService.dashboard(PORTFOLIO_ID))
                .willThrow(new ResourceNotFoundException("PORTFOLIO_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/portfolios/{portfolioId}/dashboard", PORTFOLIO_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PORTFOLIO_NOT_FOUND"));
    }

    @Test
    void performance_returnsPointsAndBaseCurrency() throws Exception {
        given(analyticsService.performance(eq(PORTFOLIO_ID), any(), any()))
                .willReturn(samplePerformance());

        mockMvc.perform(get("/api/v1/portfolios/{portfolioId}/performance", PORTFOLIO_ID)
                        .queryParam("from", "2026-07-01")
                        .queryParam("to", "2026-07-27"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portfolioId").value(PORTFOLIO_ID))
                .andExpect(jsonPath("$.baseCurrency").value("USD"))
                .andExpect(jsonPath("$.points[0].valuationDate").value("2026-07-23"))
                .andExpect(jsonPath("$.points[0].returnPct").value("13.46363636"))
                .andExpect(jsonPath("$.points[0].pricedPositionCount").value(2));
    }

    @Test
    void performance_invalidDateRange_returns422Code() throws Exception {
        given(analyticsService.performance(eq(PORTFOLIO_ID), any(), any()))
                .willThrow(new InvalidDateRangeException("from must be on or before to"));

        mockMvc.perform(get("/api/v1/portfolios/{portfolioId}/performance", PORTFOLIO_ID)
                        .queryParam("from", "2026-07-27")
                        .queryParam("to", "2026-07-01"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
    }

    private DashboardResponse sampleDashboard() {
        return new DashboardResponse(
                new PortfolioInfoResponse(PORTFOLIO_ID, "Long-term Growth", "USD"),
                new PortfolioSummaryResponse(
                        PORTFOLIO_ID,
                        2,
                        2,
                        0,
                        new BigDecimal("125430.18000000"),
                        new BigDecimal("110000.00000030"),
                        new BigDecimal("110000.00000030"),
                        new BigDecimal("15430.17999970"),
                        new BigDecimal("14.02743636"),
                        LocalDate.of(2026, 7, 24),
                        LocalDate.of(2026, 7, 24)),
                List.of(new DashboardPositionResponse(
                        "33333333-3333-3333-3333-333333333333",
                        "AAPL",
                        "Apple Inc.",
                        AssetType.STOCK,
                        new BigDecimal("400.00000000"),
                        new BigDecimal("198.54500000"),
                        new BigDecimal("79418.00000000"),
                        new BigDecimal("213.55000000"),
                        LocalDate.of(2026, 7, 24),
                        "twelve-data",
                        PriceStatus.FRESH,
                        new BigDecimal("85420.00000000"),
                        new BigDecimal("6002.00000000"),
                        new BigDecimal("7.55748067"),
                        new BigDecimal("68.10163232"))),
                List.of(new AllocationItemResponse(
                        "33333333-3333-3333-3333-333333333333",
                        "AAPL",
                        new BigDecimal("85420.00000000"),
                        new BigDecimal("68.10163232"))));
    }

    private PerformanceResponse samplePerformance() {
        return new PerformanceResponse(
                PORTFOLIO_ID,
                "USD",
                List.of(new PerformancePointResponse(
                        LocalDate.of(2026, 7, 23),
                        new BigDecimal("124810.00000000"),
                        new BigDecimal("110000.00000000"),
                        new BigDecimal("110000.00000000"),
                        new BigDecimal("14810.00000000"),
                        new BigDecimal("13.46363636"),
                        2,
                        0)));
    }
}
