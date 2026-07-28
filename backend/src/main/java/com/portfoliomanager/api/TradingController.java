package com.portfoliomanager.api;

import com.portfoliomanager.api.ApiModels.InstrumentListResponse;
import com.portfoliomanager.api.ApiModels.PageResponse;
import com.portfoliomanager.api.ApiModels.PositionListResponse;
import com.portfoliomanager.api.ApiModels.PositionResponse;
import com.portfoliomanager.api.ApiModels.TransactionCreateRequest;
import com.portfoliomanager.api.ApiModels.TransactionResponse;
import com.portfoliomanager.service.TradingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Validated
@Tag(name = "Trading", description = "Instrument search, trade execution, and position management")
public class TradingController {

    private final TradingService service;

    public TradingController(TradingService service) {
        this.service = service;
    }

    @GetMapping("/instruments")
    @Operation(
            summary = "List or search instruments",
            description = "List active stocks and ETFs, or filter by symbol or name (case-insensitive substring match)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Search successful"),
        @ApiResponse(responseCode = "422", description = "Validation error")
    })
    public InstrumentListResponse searchInstruments(
            @RequestParam(required = false)
                    @Schema(description = "Search query (symbol or name fragment)")
                    String query,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        return new InstrumentListResponse(service.searchInstruments(query, limit));
    }

    @PostMapping("/portfolios/{portfolioId}/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Record trade transaction",
            description = "Record a buy or sell at an exact stored one-minute bar close "
                    + "and atomically update the position. "
                    + "Idempotency-Key header ensures the same request is never double-executed.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Transaction created"),
        @ApiResponse(responseCode = "404", description = "Portfolio or instrument not found"),
        @ApiResponse(responseCode = "409", description = "Conflict (instrument inactive, insufficient quantity, etc.)"),
        @ApiResponse(responseCode = "422", description = "Validation error")
    })
    public TransactionResponse createTransaction(
            @PathVariable String portfolioId,
            @Valid @RequestBody TransactionCreateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false)
                    String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }
        return service.createTransaction(portfolioId, idempotencyKey.trim(), request);
    }

    @GetMapping("/portfolios/{portfolioId}/transactions")
    @Operation(summary = "List transactions", description = "Get transaction history for a portfolio, ordered by execution time (newest first)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Success"),
        @ApiResponse(responseCode = "404", description = "Portfolio not found"),
        @ApiResponse(responseCode = "422", description = "Validation error")
    })
    public PageResponse<TransactionResponse> listTransactions(
            @PathVariable String portfolioId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return service.listTransactions(portfolioId, page, pageSize);
    }

    @GetMapping("/portfolios/{portfolioId}/positions")
    @Operation(
            summary = "List positions",
            description = "Get current holdings (quantity > 0) for a portfolio")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Success"),
        @ApiResponse(responseCode = "404", description = "Portfolio not found")
    })
    public PositionListResponse listPositions(@PathVariable String portfolioId) {
        return new PositionListResponse(service.listPositions(portfolioId));
    }
}
