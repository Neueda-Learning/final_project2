package com.portfoliomanager.service;

import com.portfoliomanager.api.ApiModels.PageResponse;
import com.portfoliomanager.api.ApiModels.PortfolioCreateRequest;
import com.portfoliomanager.api.ApiModels.PortfolioResponse;
import com.portfoliomanager.config.WebConfig;
import com.portfoliomanager.domain.model.AppUser;
import com.portfoliomanager.domain.model.Portfolio;
import com.portfoliomanager.repository.AppUserRepository;
import com.portfoliomanager.repository.PortfolioRepository;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortfolioService {

    private final PortfolioRepository portfolios;
    private final AppUserRepository users;
    private final WebConfig config;

    public PortfolioService(
            PortfolioRepository portfolios,
            AppUserRepository users,
            WebConfig config) {
        this.portfolios = portfolios;
        this.users = users;
        this.config = config;
    }

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

    @Transactional
    public PortfolioResponse create(PortfolioCreateRequest request) {
        AppUser user = users.findById(config.getDemoUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Demo user not found"));
        var portfolio = new Portfolio(
                UUID.randomUUID().toString(),
                user,
                request.name().trim(),
                request.description(),
                request.baseCurrency());
        return toResponse(portfolios.save(portfolio));
    }

    @Transactional(readOnly = true)
    public PortfolioResponse get(String id) {
        return portfolios.findById(id)
                .map(PortfolioService::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found"));
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
