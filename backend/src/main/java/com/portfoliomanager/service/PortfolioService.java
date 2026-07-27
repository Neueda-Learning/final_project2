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

    /** 分页列出当前演示用户的所有活跃（未归档）组合，按创建时间倒序 */
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
     * 创建新组合。
     * 业务规则：同一用户的活跃组合名称大小写不敏感唯一，违反时抛 ConflictException。
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

    /** 按 ID 获取单个组合，同时校验所有权（非当前用户的组合返回 404，不泄露信息） */
    @Transactional(readOnly = true)
    public PortfolioResponse get(String id) {
        return portfolios.findByIdAndUserId(id, config.getDemoUserId())
                .map(PortfolioService::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found"));
    }

    /**
     * 部分更新组合（name / description）。
     * name 和 description 均为可选：传 null 表示不修改该字段。
     * 修改 name 时会重新校验名称唯一性（排除自身）。
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
     * 硬删除组合。
     * 业务规则：有交易历史的组合不允许删除，需先归档（抛 PORTFOLIO_HAS_TRADES）。
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
     * 归档组合（软删除）。
     * 归档后：不出现在默认列表、名称释放可被新组合使用、数据永久保留。
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
