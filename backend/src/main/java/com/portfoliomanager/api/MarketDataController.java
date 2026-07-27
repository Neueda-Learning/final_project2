package com.portfoliomanager.api;

import com.portfoliomanager.api.ApiModels.SyncRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/market-data")
public class MarketDataController {

    @PostMapping("/sync")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> requestSync(@RequestBody SyncRequest request) {
        return Map.of(
                "status", "accepted",
                "force", request.force(),
                "instrumentIds", request.instrumentIds() == null ? java.util.List.of() : request.instrumentIds());
    }
}
