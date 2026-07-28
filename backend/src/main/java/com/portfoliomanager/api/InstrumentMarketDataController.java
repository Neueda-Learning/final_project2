package com.portfoliomanager.api;

import com.portfoliomanager.api.ApiModels.MarketPriceResponse;
import com.portfoliomanager.api.ApiModels.MarketBarPageResponse;
import com.portfoliomanager.service.MarketDataService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/v1/instruments")
@Validated
public class InstrumentMarketDataController {

    private final MarketDataService marketDataService;

    public InstrumentMarketDataController(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    @GetMapping("/{instrumentId}/latest-price")
    public MarketPriceResponse latestPrice(@PathVariable String instrumentId) {
        return marketDataService.latestPrice(instrumentId);
    }

    @GetMapping("/{instrumentId}/tradable-prices")
    public List<MarketPriceResponse> tradablePrices(
            @PathVariable String instrumentId,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "60")
                    @Min(1) @Max(250) int limit) {
        return marketDataService.tradablePrices(instrumentId, limit);
    }

    @GetMapping("/{instrumentId}/bars")
    public ResponseEntity<MarketBarPageResponse> bars(
            @PathVariable String instrumentId,
            @RequestParam(defaultValue = "1min")
                    @Pattern(regexp = "1min|5min|15min|30min") String interval,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "1") @Min(1) @Max(100000) int page,
            @RequestParam(defaultValue = "200") @Min(1) @Max(500) int pageSize) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(15)).cachePrivate())
                .body(marketDataService.bars(
                        instrumentId, interval, from, to, page, pageSize));
    }
}
