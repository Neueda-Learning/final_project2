package com.portfoliomanager.api;

import com.portfoliomanager.api.ApiModels.MarketPriceResponse;
import com.portfoliomanager.service.MarketDataService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/instruments")
public class InstrumentMarketDataController {

    private final MarketDataService marketDataService;

    public InstrumentMarketDataController(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    @GetMapping("/{instrumentId}/latest-price")
    public MarketPriceResponse latestPrice(@PathVariable String instrumentId) {
        return marketDataService.latestPrice(instrumentId);
    }
}
