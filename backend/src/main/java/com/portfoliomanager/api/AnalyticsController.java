package com.portfoliomanager.api;

import com.portfoliomanager.api.ApiModels.DashboardResponse;
import com.portfoliomanager.api.ApiModels.PerformanceResponse;
import com.portfoliomanager.service.AnalyticsService;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/portfolios/{portfolioId}")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard(@PathVariable String portfolioId) {
        return analyticsService.dashboard(portfolioId);
    }

    @GetMapping("/performance")
    public PerformanceResponse performance(
            @PathVariable String portfolioId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return analyticsService.performance(portfolioId, from, to);
    }
}
