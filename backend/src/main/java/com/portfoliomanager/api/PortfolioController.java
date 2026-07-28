package com.portfoliomanager.api;

import com.portfoliomanager.api.ApiModels.PageResponse;
import com.portfoliomanager.api.ApiModels.PortfolioCreateRequest;
import com.portfoliomanager.api.ApiModels.PortfolioResponse;
import com.portfoliomanager.api.ApiModels.PortfolioUpdateRequest;
import com.portfoliomanager.service.PortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/portfolios")
@Tag(name = "Portfolios", description = "Portfolio management APIs")
public class PortfolioController {

    private final PortfolioService service;

    public PortfolioController(PortfolioService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List portfolios", description = "Returns all active portfolios for the current user")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Success"),
        @ApiResponse(responseCode = "422", description = "Validation error")
    })
    public PageResponse<PortfolioResponse> list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return service.list(page, pageSize);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create portfolio")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Portfolio created"),
        @ApiResponse(responseCode = "409", description = "Portfolio name conflict"),
        @ApiResponse(responseCode = "422", description = "Validation error")
    })
    public PortfolioResponse create(@Valid @RequestBody PortfolioCreateRequest request) {
        return service.create(request);
    }

    @GetMapping("/{portfolioId}")
    @Operation(summary = "Get portfolio by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Success"),
        @ApiResponse(responseCode = "404", description = "Portfolio not found")
    })
    public PortfolioResponse get(@PathVariable String portfolioId) {
        return service.get(portfolioId);
    }

    @PatchMapping("/{portfolioId}")
    @Operation(summary = "Update portfolio")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Portfolio updated"),
        @ApiResponse(responseCode = "404", description = "Portfolio not found"),
        @ApiResponse(responseCode = "409", description = "Portfolio name conflict"),
        @ApiResponse(responseCode = "422", description = "Validation error")
    })
    public PortfolioResponse update(
            @PathVariable String portfolioId,
            @Valid @RequestBody PortfolioUpdateRequest request) {
        return service.update(portfolioId, request);
    }

    @DeleteMapping("/{portfolioId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete portfolio", description = "Hard delete; fails if portfolio has trade history")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Portfolio deleted"),
        @ApiResponse(responseCode = "404", description = "Portfolio not found"),
        @ApiResponse(responseCode = "409", description = "Portfolio has trade history")
    })
    public void delete(@PathVariable String portfolioId) {
        service.delete(portfolioId);
    }

    @PostMapping("/{portfolioId}/archive")
    @Operation(summary = "Archive portfolio")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Portfolio archived"),
        @ApiResponse(responseCode = "404", description = "Portfolio not found")
    })
    public PortfolioResponse archive(@PathVariable String portfolioId) {
        return service.archive(portfolioId);
    }
}
