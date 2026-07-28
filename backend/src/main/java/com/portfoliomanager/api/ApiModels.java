package com.portfoliomanager.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.portfoliomanager.domain.AssetType;
import com.portfoliomanager.domain.PriceStatus;
import com.portfoliomanager.domain.SyncStatus;
import com.portfoliomanager.domain.SyncTrigger;
import com.portfoliomanager.domain.TradeSide;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class ApiModels {

    private ApiModels() {}

    public record ErrorDetail(String field, String message) {}

    public record ErrorResponse(
            String code,
            String message,
            List<ErrorDetail> details,
            String requestId) {}

    public record PageResponse<T>(
            List<T> items,
            int page,
            int pageSize,
            long total) {}

    public record PortfolioCreateRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description,
            @NotNull @Pattern(regexp = "[A-Z]{3}") String baseCurrency) {}

    public record PortfolioUpdateRequest(
            @Size(min = 1, max = 120) String name,
            @Size(max = 500) String description,
            Boolean archived) {}

    public record PortfolioResponse(
            String id,
            String name,
            String description,
            String baseCurrency,
            boolean isArchived,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}

    public record InstrumentResponse(
            String id,
            String symbol,
            String name,
            AssetType assetType,
            String exchangeCode,
            String currency,
            boolean isActive) {}

    public record TransactionCreateRequest(
            @NotBlank String instrumentId,
            @NotNull TradeSide side,
            @NotNull @Positive @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal quantity,
            @NotNull @Positive @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal unitPrice,
            @NotNull @DecimalMin("0") @JsonFormat(shape = JsonFormat.Shape.STRING)
                    BigDecimal feeAmount,
            @NotNull @Pattern(regexp = "[A-Z]{3}") String currency,
            @NotNull LocalDateTime executedAt,
            @Size(max = 500) String note) {}

    public record TransactionResponse(
            String id,
            String portfolioId,
            String instrumentId,
            TradeSide side,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal quantity,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal unitPrice,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal feeAmount,
            String currency,
            LocalDateTime executedAt,
            String idempotencyKey,
            String note,
            LocalDateTime createdAt) {}

    public record PositionResponse(
            String portfolioId,
            InstrumentResponse instrument,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal quantity,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal averageCost,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal realizedPnl,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal costBasis,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal closePrice,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal marketValue,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal unrealizedPnl,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal returnPct,
            LocalDate priceDate,
            PriceStatus priceStatus) {}

    public record SyncRequest(boolean force) {}

    public record SyncRunResponse(
            String id,
            String provider,
            SyncStatus status,
            int requestedCount,
            int successCount,
            int failureCount,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            SyncTrigger triggeredBy,
            String errorSummary) {}

    public record MarketPriceResponse(
            String instrumentId,
            String symbol,
            LocalDate priceDate,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal closePrice,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal adjustedClose,
            String currency,
            String source,
            LocalDateTime sourceTimestamp,
            LocalDateTime fetchedAt,
            PriceStatus priceStatus) {}

    public record PortfolioSummaryResponse(
            String portfolioId,
            int positionCount,
            int pricedPositionCount,
            int unpricedPositionCount,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal pricedMarketValue,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalCostBasis,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal pricedCostBasis,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal unrealizedPnl,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal returnPct,
            LocalDate newestPriceDate,
            LocalDate oldestUsedPriceDate) {}

    public record PortfolioInfoResponse(
            String id,
            String name,
            String baseCurrency) {}

    public record DashboardPositionResponse(
            String instrumentId,
            String symbol,
            String instrumentName,
            AssetType assetType,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal quantity,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal averageCost,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal costBasis,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal closePrice,
            LocalDate priceDate,
            String priceSource,
            PriceStatus priceStatus,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal marketValue,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal unrealizedPnl,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal returnPct,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal allocationPct) {}

    public record AllocationItemResponse(
            String instrumentId,
            String symbol,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal marketValue,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal allocationPct) {}

    public record DashboardResponse(
            PortfolioInfoResponse portfolio,
            PortfolioSummaryResponse summary,
            List<DashboardPositionResponse> positions,
            List<AllocationItemResponse> allocation) {}

    public record PerformancePointResponse(
            LocalDate valuationDate,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal pricedMarketValue,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalCostBasis,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal pricedCostBasis,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal unrealizedPnl,
            int pricedPositionCount,
            int unpricedPositionCount) {}

    public record PerformanceResponse(
            String portfolioId,
            String baseCurrency,
            List<PerformancePointResponse> points) {}

    public record HealthLiveResponse(String status) {}

    public record HealthReadyResponse(String status, java.util.Map<String, String> checks) {}
}
