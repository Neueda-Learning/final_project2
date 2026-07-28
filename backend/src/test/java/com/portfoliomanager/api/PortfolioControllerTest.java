package com.portfoliomanager.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.portfoliomanager.api.ApiModels.PageResponse;
import com.portfoliomanager.api.ApiModels.PortfolioCreateRequest;
import com.portfoliomanager.api.ApiModels.PortfolioResponse;
import com.portfoliomanager.api.ApiModels.PortfolioUpdateRequest;
import com.portfoliomanager.service.ConflictException;
import com.portfoliomanager.service.PortfolioService;
import com.portfoliomanager.service.ResourceNotFoundException;
import java.time.LocalDateTime;
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
class PortfolioControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private PortfolioService portfolioService;

    private static final String BASE_URL = "/api/v1/portfolios";
    private static final String PORTFOLIO_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new PortfolioController(portfolioService))
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private PortfolioResponse sampleResponse() {
        return new PortfolioResponse(
                PORTFOLIO_ID, "My Portfolio", "Test description", "USD", false,
                LocalDateTime.of(2024, 1, 1, 0, 0),
                LocalDateTime.of(2024, 1, 1, 0, 0));
    }

    @Test
    void list_returnsPagedPortfolios() throws Exception {
        given(portfolioService.list(1, 20))
                .willReturn(new PageResponse<>(List.of(sampleResponse()), 1, 20, 1L));

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(PORTFOLIO_ID))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void create_validRequest_returns201() throws Exception {
        given(portfolioService.create(any())).willReturn(sampleResponse());

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"My Portfolio\",\"baseCurrency\":\"USD\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(PORTFOLIO_ID));
    }

    @Test
    void create_blankName_returns422() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"baseCurrency\":\"USD\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void create_nameConflict_returns409() throws Exception {
        given(portfolioService.create(any()))
                .willThrow(new ConflictException("PORTFOLIO_NAME_CONFLICT"));

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Existing\",\"baseCurrency\":\"USD\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PORTFOLIO_NAME_CONFLICT"));
    }

    @Test
    void get_existingPortfolio_returns200() throws Exception {
        given(portfolioService.get(PORTFOLIO_ID)).willReturn(sampleResponse());

        mockMvc.perform(get(BASE_URL + "/" + PORTFOLIO_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseCurrency").value("USD"));
    }

    @Test
    void get_unknownId_returns404() throws Exception {
        given(portfolioService.get("nonexistent"))
                .willThrow(new ResourceNotFoundException("Portfolio not found"));

        mockMvc.perform(get(BASE_URL + "/nonexistent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void update_validRequest_returns200() throws Exception {
        var updated = new PortfolioResponse(
                PORTFOLIO_ID, "Renamed", "New desc", "USD", false,
                LocalDateTime.of(2024, 1, 1, 0, 0),
                LocalDateTime.of(2024, 6, 1, 0, 0));
        given(portfolioService.update(eq(PORTFOLIO_ID), any())).willReturn(updated);

        mockMvc.perform(patch(BASE_URL + "/" + PORTFOLIO_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\",\"description\":\"New desc\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"));
    }

    @Test
    void update_unknownId_returns404() throws Exception {
        given(portfolioService.update(eq("notexist"), any()))
                .willThrow(new ResourceNotFoundException("Portfolio not found"));

        mockMvc.perform(patch(BASE_URL + "/notexist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_nameConflict_returns409() throws Exception {
        given(portfolioService.update(eq(PORTFOLIO_ID), any()))
                .willThrow(new ConflictException("PORTFOLIO_NAME_CONFLICT"));

        mockMvc.perform(patch(BASE_URL + "/" + PORTFOLIO_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Conflict Name\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PORTFOLIO_NAME_CONFLICT"));
    }

    @Test
    void delete_emptyPortfolio_returns204() throws Exception {
        willDoNothing().given(portfolioService).delete(PORTFOLIO_ID);

        mockMvc.perform(delete(BASE_URL + "/" + PORTFOLIO_ID))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_portfolioWithTrades_returns409() throws Exception {
        willThrow(new ConflictException("PORTFOLIO_HAS_TRADES"))
                .given(portfolioService).delete(PORTFOLIO_ID);

        mockMvc.perform(delete(BASE_URL + "/" + PORTFOLIO_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PORTFOLIO_HAS_TRADES"));
    }

    @Test
    void delete_unknownId_returns404() throws Exception {
        willThrow(new ResourceNotFoundException("Portfolio not found"))
                .given(portfolioService).delete("nope");

        mockMvc.perform(delete(BASE_URL + "/nope"))
                .andExpect(status().isNotFound());
    }

    @Test
    void archive_existingPortfolio_returns200WithArchivedTrue() throws Exception {
        var archived = new PortfolioResponse(
                PORTFOLIO_ID, "My Portfolio", "desc", "USD", true,
                LocalDateTime.of(2024, 1, 1, 0, 0),
                LocalDateTime.of(2024, 6, 1, 0, 0));
        given(portfolioService.archive(PORTFOLIO_ID)).willReturn(archived);

        mockMvc.perform(post(BASE_URL + "/" + PORTFOLIO_ID + "/archive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isArchived").value(true));
    }

    @Test
    void archive_unknownId_returns404() throws Exception {
        given(portfolioService.archive("missing"))
                .willThrow(new ResourceNotFoundException("Portfolio not found"));

        mockMvc.perform(post(BASE_URL + "/missing/archive"))
                .andExpect(status().isNotFound());
    }
}
