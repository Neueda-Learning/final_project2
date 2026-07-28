package com.portfoliomanager.service;

import com.portfoliomanager.api.ApiModels.PageResponse;
import com.portfoliomanager.api.ApiModels.PortfolioCreateRequest;
import com.portfoliomanager.api.ApiModels.PortfolioResponse;
import com.portfoliomanager.api.ApiModels.PortfolioUpdateRequest;
import com.portfoliomanager.config.WebConfig;
import com.portfoliomanager.domain.model.AppUser;
import com.portfoliomanager.domain.model.Portfolio;
import com.portfoliomanager.repository.AppUserRepository;
import com.portfoliomanager.repository.PortfolioRepository;
import com.portfoliomanager.repository.TradeTransactionRepository;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortfolioService {

    private final PortfolioRepository portfolios;
    private final AppUserRepository users;
    private final TradeTransactionRepository trades;
    private final WebConfig config;

    public PortfolioService(
            PortfolioRepository portfolios,
            AppUserRepository users,
            TradeTransactionRepository trades,
            WebConfig config) {
        this.portfolios = portfolios;
        this.users = users;
        this.trades = trades;
        this.config = config;
    }

    /** Lists active portfolios for the demo user, newest first. */
    @Transactional(readOnly = true)
    public PageResponse<PortfolioResponse> list(int page, int pageSize) {
        var result = portfolios.findByUserIdAndArchivedFalse(
                config.getDemoUserId(),
                PageRequest.of(page - 1, pageSize, Sort.by("createdAt").descending()));
        return new PageResponse<>(
                result.stream().map(PortfolioService::toResponse).toList(),
                page,
                pageSize,
                result.getTotalElements());
    }

    /**
     * Creates a portfolio and enforces case-insensitive active-name uniqueness.
     */
    @Transactional
    public PortfolioResponse create(PortfolioCreateRequest request) {
        String userId = config.getDemoUserId();
        AppUser user = users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Demo user not found"));
        String trimmedName = request.name().trim();
        if (portfolios.existsActiveByUserIdAndNameIgnoreCase(userId, trimmedName)) {
            throw new ConflictException("PORTFOLIO_NAME_CONFLICT");
        }
        var portfolio = new Portfolio(
                UUID.randomUUID().toString(),
                user,
                trimmedName,
                request.description(),
                request.baseCurrency());
        return toResponse(portfolios.save(portfolio));
    }

    /** Gets a portfolio by ID while enforcing ownership through a 404 boundary. */
    @Transactional(readOnly = true)
    public PortfolioResponse get(String id) {
        return portfolios.findByIdAndUserId(id, config.getDemoUserId())
                .map(PortfolioService::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found"));
    }

    /**
     * Partially updates a portfolio and revalidates name uniqueness when needed.
     */
    @Transactional
    public PortfolioResponse update(String id, PortfolioUpdateRequest request) {
        String userId = config.getDemoUserId();
        Portfolio portfolio = portfolios.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found"));
        if (request.name() != null) {
            String trimmedName = request.name().trim();
            if (portfolios.existsActiveByUserIdAndNameIgnoreCaseExcluding(userId, trimmedName, id)) {
                throw new ConflictException("PORTFOLIO_NAME_CONFLICT");
            }
            portfolio.setName(trimmedName);
        }
        if (request.description() != null) {
            portfolio.setDescription(request.description());
        }
        return toResponse(portfolios.save(portfolio));
    }

    /**
     * Hard-deletes a portfolio only when it has no transaction history.
     */
    @Transactional
    public void delete(String id) {
        String userId = config.getDemoUserId();
        Portfolio portfolio = portfolios.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found"));
        if (trades.existsByPortfolioId(id)) {
            throw new ConflictException("PORTFOLIO_HAS_TRADES");
        }
        portfolios.delete(portfolio);
    }

    /**
     * Archives a portfolio while preserving its data and releasing its active name.
     */
    @Transactional
    public PortfolioResponse archive(String id) {
        String userId = config.getDemoUserId();
        Portfolio portfolio = portfolios.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found"));
        portfolio.setArchived(true);
        return toResponse(portfolios.save(portfolio));
    }

    private static PortfolioResponse toResponse(Portfolio portfolio) {
        return new PortfolioResponse(
                portfolio.getId(),
                portfolio.getName(),
                portfolio.getDescription(),
                portfolio.getBaseCurrency(),
                portfolio.isArchived(),
                portfolio.getCreatedAt(),
                portfolio.getUpdatedAt());
    }
}
