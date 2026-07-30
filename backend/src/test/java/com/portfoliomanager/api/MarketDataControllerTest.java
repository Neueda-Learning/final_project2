package com.portfoliomanager.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.portfoliomanager.api.ApiModels.SyncRunResponse;
import com.portfoliomanager.domain.SyncStage;
import com.portfoliomanager.domain.SyncStatus;
import com.portfoliomanager.domain.SyncTrigger;
import com.portfoliomanager.service.MarketDataService;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MarketDataControllerTest {

    private MarketDataService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = Mockito.mock(MarketDataService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new MarketDataController(service)).build();
    }

    @Test
    void manualSyncReturnsAcceptedRun() throws Exception {
        when(service.requestManualSync(false)).thenReturn(runningSync());

        mockMvc.perform(post("/api/v1/market-data/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"force\":false}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.stage").value("FETCHING_MARKET_DATA"))
                .andExpect(jsonPath("$.triggeredBy").value("MANUAL"));
    }

    @Test
    void latestRunReturnsNullWhenNoSyncHasRun() throws Exception {
        when(service.latestSyncRun()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/market-data/sync-runs/latest"))
                .andExpect(status().isOk());
    }

    @Test
    void latestRun_whenSyncExists_returnsRunWithAllFields() throws Exception {
        when(service.latestSyncRun()).thenReturn(Optional.of(completedSync()));

        mockMvc.perform(get("/api/v1/market-data/sync-runs/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("run-2"))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.stage").value("COMPLETED"))
                .andExpect(jsonPath("$.successCount").value(42))
                .andExpect(jsonPath("$.triggeredBy").value("SCHEDULE"));
    }

    @Test
    void runById_whenSyncExists_returnsRunWithAllFields() throws Exception {
        when(service.syncRunById("run-2")).thenReturn(Optional.of(completedSync()));

        mockMvc.perform(get("/api/v1/market-data/sync-runs/run-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("run-2"))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.stage").value("COMPLETED"));
    }

    @Test
    void requestSync_withForceTrue_passesForceToService() throws Exception {
        when(service.requestManualSync(true)).thenReturn(runningSync());

        mockMvc.perform(post("/api/v1/market-data/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"force\":true}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void requestInstrumentSync_returnsAcceptedRun() throws Exception {
        when(service.requestInstrumentSync("instrument-1", false)).thenReturn(runningSync());

        mockMvc.perform(post("/api/v1/market-data/sync/instruments/instrument-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"force\":false}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.triggeredBy").value("MANUAL"));
    }

    @Test
    void requestSync_completedSyncRun_returnsSucceededStatus() throws Exception {
        when(service.requestManualSync(false)).thenReturn(completedSync());

        mockMvc.perform(post("/api/v1/market-data/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"force\":false}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.successCount").value(42))
                .andExpect(jsonPath("$.errorSummary").doesNotExist());
    }

    private SyncRunResponse runningSync() {
        return new SyncRunResponse(
                "run-1",
                "fixture",
                SyncStatus.RUNNING,
                SyncStage.FETCHING_MARKET_DATA,
                2,
                0,
                0,
                LocalDateTime.of(2026, 7, 27, 9, 0),
                null,
                SyncTrigger.MANUAL,
                null);
    }

    private SyncRunResponse completedSync() {
        return new SyncRunResponse(
                "run-2",
                "alpaca",
                SyncStatus.SUCCEEDED,
                SyncStage.COMPLETED,
                42,
                42,
                0,
                LocalDateTime.of(2026, 7, 27, 22, 0),
                LocalDateTime.of(2026, 7, 27, 22, 5),
                SyncTrigger.SCHEDULE,
                null);
    }
}
