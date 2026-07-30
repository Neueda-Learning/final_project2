package com.portfoliomanager.api;

import com.portfoliomanager.api.ApiModels.SyncRequest;
import com.portfoliomanager.api.ApiModels.SyncRunResponse;
import com.portfoliomanager.service.MarketDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/market-data")
@Validated
public class MarketDataController {

    private final MarketDataService marketDataService;

    public MarketDataController(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    @PostMapping("/sync")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SyncRunResponse requestSync(@RequestBody SyncRequest request) {
        return marketDataService.requestManualSync(request.force());
    }

    @PostMapping("/sync/instruments/{instrumentId}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SyncRunResponse requestInstrumentSync(
            @PathVariable String instrumentId,
            @RequestBody SyncRequest request) {
        return marketDataService.requestInstrumentSync(instrumentId, request.force());
    }

    @GetMapping("/sync-runs/latest")
    public ResponseEntity<SyncRunResponse> latestSyncRun() {
        return ResponseEntity.ok(marketDataService.latestSyncRun().orElse(null));
    }

    @GetMapping("/sync-runs/{runId}")
    public ResponseEntity<SyncRunResponse> syncRunById(@PathVariable String runId) {
        return ResponseEntity.ok(marketDataService.syncRunById(runId).orElse(null));
    }
}
