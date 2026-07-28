package com.portfoliomanager.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.portfoliomanager.api.ApiModels.InstrumentResponse;
import com.portfoliomanager.api.ApiModels.PositionResponse;
import com.portfoliomanager.api.ApiModels.TransactionCreateRequest;
import com.portfoliomanager.api.ApiModels.TransactionResponse;
import com.portfoliomanager.domain.AssetType;
import com.portfoliomanager.domain.TradeSide;
import com.portfoliomanager.service.TradingService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class TradingControllerTest {

    private static final String PORTFOLIO_ID = "22222222-2222-2222-2222-222222222222";

    private MockMvc mockMvc;

    @Mock
    private TradingService tradingService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new TradingController(tradingService))
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void positions_returnsDocumentedListEnvelopeAndFlatItems() throws Exception {
        given(tradingService.listPositions(PORTFOLIO_ID)).willReturn(List.of(
                new PositionResponse(
                        "33333333-3333-3333-3333-333333333333",
                        "AAPL",
                        "Apple Inc.",
                        AssetType.STOCK,
                        new BigDecimal("10.00000000"),
                        new BigDecimal("198.54500000"),
                        new BigDecimal("0.00000000"),
                        LocalDateTime.parse("2026-07-27T08:30:01"),
                        LocalDateTime.parse("2026-07-27T08:30:01"))));

        mockMvc.perform(get("/api/v1/portfolios/{portfolioId}/positions", PORTFOLIO_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].instrumentId")
                        .value("33333333-3333-3333-3333-333333333333"))
                .andExpect(jsonPath("$.items[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$.items[0].quantity").value("10.00000000"))
                .andExpect(jsonPath("$.items[0].openedAt").value("2026-07-27T08:30:01"))
                .andExpect(jsonPath("$.items[0].instrument").doesNotExist());
    }

    @Test
    void instruments_returnsSearchResultsInTheDocumentedEnvelope() throws Exception {
        given(tradingService.searchInstruments("AAPL", 10))
                .willReturn(List.of(new InstrumentResponse(
                        "33333333-3333-3333-3333-333333333333",
                        "AAPL",
                        "Apple Inc.",
                        AssetType.STOCK,
                        "NASDAQ",
                        "USD",
                        true)));

        mockMvc.perform(get("/api/v1/instruments")
                        .param("query", "AAPL")
                        .param("limit", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].symbol").value("AAPL"));
    }

    @Test
    void createTransactionAcceptsMinuteTimestampAndReturnsTheServerResolvedPrice() throws Exception {
        LocalDateTime executionTimestamp = LocalDateTime.of(2026, 7, 27, 19, 59);
        given(tradingService.createTransaction(eq(PORTFOLIO_ID), eq("trade-key"), any()))
                .willReturn(new TransactionResponse(
                        "transaction-id",
                        PORTFOLIO_ID,
                        "33333333-3333-3333-3333-333333333333",
                        "AAPL",
                        TradeSide.BUY,
                        new BigDecimal("2"),
                        new BigDecimal("214.05"),
                        BigDecimal.ZERO,
                        "USD",
                        executionTimestamp,
                        null,
                        LocalDateTime.of(2026, 7, 28, 8, 0)));

        mockMvc.perform(post("/api/v1/portfolios/{portfolioId}/transactions", PORTFOLIO_ID)
                        .header("Idempotency-Key", "trade-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "instrumentId": "33333333-3333-3333-3333-333333333333",
                                  "side": "BUY",
                                  "quantity": "2",
                                  "executionTimestamp": "2026-07-27T19:59:00",
                                  "feeAmount": "0"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.unitPrice").value("214.05"))
                .andExpect(jsonPath("$.executedAt").value("2026-07-27T19:59:00"))
                .andExpect(jsonPath("$.idempotencyKey").doesNotExist());

        ArgumentCaptor<TransactionCreateRequest> requestCaptor =
                ArgumentCaptor.forClass(TransactionCreateRequest.class);
        verify(tradingService).createTransaction(
                eq(PORTFOLIO_ID), eq("trade-key"), requestCaptor.capture());
        assertThat(requestCaptor.getValue().executionTimestamp()).isEqualTo(executionTimestamp);
    }
}
