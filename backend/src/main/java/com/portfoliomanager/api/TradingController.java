package com.portfoliomanager.api;

import com.portfoliomanager.api.ApiModels.PageResponse;
import com.portfoliomanager.api.ApiModels.PositionResponse;
import com.portfoliomanager.api.ApiModels.TransactionResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/portfolios/{portfolioId}")
public class TradingController {

    @GetMapping("/transactions")
    public PageResponse<TransactionResponse> transactions(@PathVariable String portfolioId) {
        return new PageResponse<>(List.of(), 1, 20, 0);
    }

    @GetMapping("/positions")
    public List<PositionResponse> positions(@PathVariable String portfolioId) {
        return List.of();
    }
}
