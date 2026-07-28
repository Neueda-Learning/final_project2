package com.portfoliomanager.api;

import com.portfoliomanager.api.ApiModels.MarketPriceResponse;
import com.portfoliomanager.service.MarketDataService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
