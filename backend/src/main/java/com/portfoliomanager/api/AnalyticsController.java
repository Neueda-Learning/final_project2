package com.portfoliomanager.api;

import com.portfoliomanager.api.ApiModels.DashboardResponse;
import com.portfoliomanager.api.ApiModels.PerformanceResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/portfolios/{portfolioId}")
public class AnalyticsController {

    @GetMapping("/dashboard")
    public DashboardResponse dashboard(@PathVariable String portfolioId) {
        return new DashboardResponse(portfolioId, null, List.of(), List.of());
    }

    @GetMapping("/performance")
    public PerformanceResponse performance(@PathVariable String portfolioId) {
        return new PerformanceResponse(portfolioId, List.of());
    }
}
