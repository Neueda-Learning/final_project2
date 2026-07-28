package com.portfoliomanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.portfoliomanager.api.ApiModels.PortfolioCreateRequest;
import com.portfoliomanager.api.ApiModels.PortfolioUpdateRequest;
import com.portfoliomanager.config.WebConfig;
import com.portfoliomanager.domain.model.AppUser;
import com.portfoliomanager.domain.model.Portfolio;
import com.portfoliomanager.repository.AppUserRepository;
import com.portfoliomanager.repository.PortfolioRepository;
import com.portfoliomanager.repository.TradeTransactionRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class PortfolioServiceTest {

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String PORTFOLIO_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

    private PortfolioRepository portfolioRepo;
    private AppUserRepository userRepo;
    private TradeTransactionRepository tradeRepo;
    private WebConfig config;
    private PortfolioService service;

    @BeforeEach
    void setUp() {
        portfolioRepo = mock(PortfolioRepository.class);
        userRepo = mock(AppUserRepository.class);
        tradeRepo = mock(TradeTransactionRepository.class);
        config = mock(WebConfig.class);
        given(config.getDemoUserId()).willReturn(USER_ID);

        service = new PortfolioService(portfolioRepo, userRepo, tradeRepo, config);
    }

    // ── create ──────────────────────────────────────────────────────────────

    @Test
    void create_validRequest_savesPortfolio() {
        AppUser user = buildUser();
        given(userRepo.findById(USER_ID)).willReturn(Optional.of(user));
        given(portfolioRepo.existsActiveByUserIdAndNameIgnoreCase(USER_ID, "My Fund"))
                .willReturn(false);
        given(portfolioRepo.save(any())).willAnswer(inv -> inv.getArgument(0));

        var request = new PortfolioCreateRequest("My Fund", "desc", "USD");
        var response = service.create(request);

        assertThat(response.name()).isEqualTo("My Fund");
        then(portfolioRepo).should().save(any());
    }

    @Test
    void create_nameConflict_throwsConflictException() {
        AppUser user = buildUser();
        given(userRepo.findById(USER_ID)).willReturn(Optional.of(user));
        given(portfolioRepo.existsActiveByUserIdAndNameIgnoreCase(USER_ID, "Existing"))
                .willReturn(true);

        assertThatThrownBy(() -> service.create(new PortfolioCreateRequest("Existing", null, "USD")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("PORTFOLIO_NAME_CONFLICT");

        then(portfolioRepo).should(never()).save(any());
    }

    @Test
    void create_caseInsensitiveNameConflict_throws() {
        AppUser user = buildUser();
        given(userRepo.findById(USER_ID)).willReturn(Optional.of(user));
        given(portfolioRepo.existsActiveByUserIdAndNameIgnoreCase(USER_ID, "existing"))
                .willReturn(true);

        assertThatThrownBy(() -> service.create(new PortfolioCreateRequest("existing", null, "USD")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void create_userNotFound_throwsResourceNotFoundException() {
        given(userRepo.findById(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new PortfolioCreateRequest("Fund", null, "USD")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── get ─────────────────────────────────────────────────────────────────

    @Test
    void get_ownPortfolio_returnsResponse() {
        Portfolio portfolio = buildPortfolio();
        given(portfolioRepo.findByIdAndUserId(PORTFOLIO_ID, USER_ID))
                .willReturn(Optional.of(portfolio));

        var response = service.get(PORTFOLIO_ID);
        assertThat(response.id()).isEqualTo(PORTFOLIO_ID);
    }

    @Test
    void get_portfolioOfOtherUser_throws404() {
        given(portfolioRepo.findByIdAndUserId(PORTFOLIO_ID, USER_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(PORTFOLIO_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── update ──────────────────────────────────────────────────────────────

    @Test
    void update_renameToAvailableName_succeeds() {
        Portfolio portfolio = buildPortfolio();
        given(portfolioRepo.findByIdAndUserId(PORTFOLIO_ID, USER_ID))
                .willReturn(Optional.of(portfolio));
        given(portfolioRepo.existsActiveByUserIdAndNameIgnoreCaseExcluding(USER_ID, "New Name", PORTFOLIO_ID))
                .willReturn(false);
        given(portfolioRepo.save(any())).willAnswer(inv -> inv.getArgument(0));

        var response = service.update(PORTFOLIO_ID, new PortfolioUpdateRequest("New Name", null, null));
        assertThat(response.name()).isEqualTo("New Name");
    }

    @Test
    void update_renameToConflictingName_throws409() {
        Portfolio portfolio = buildPortfolio();
        given(portfolioRepo.findByIdAndUserId(PORTFOLIO_ID, USER_ID))
                .willReturn(Optional.of(portfolio));
        given(portfolioRepo.existsActiveByUserIdAndNameIgnoreCaseExcluding(USER_ID, "Taken", PORTFOLIO_ID))
                .willReturn(true);

        assertThatThrownBy(() ->
                service.update(PORTFOLIO_ID, new PortfolioUpdateRequest("Taken", null, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("PORTFOLIO_NAME_CONFLICT");
    }

    @Test
    void update_unknownPortfolio_throws404() {
        given(portfolioRepo.findByIdAndUserId(PORTFOLIO_ID, USER_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.update(PORTFOLIO_ID, new PortfolioUpdateRequest("X", null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── delete ──────────────────────────────────────────────────────────────

    @Test
    void delete_portfolioWithoutTrades_succeeds() {
        Portfolio portfolio = buildPortfolio();
        given(portfolioRepo.findByIdAndUserId(PORTFOLIO_ID, USER_ID))
                .willReturn(Optional.of(portfolio));
        given(tradeRepo.existsByPortfolioId(PORTFOLIO_ID)).willReturn(false);

        service.delete(PORTFOLIO_ID);

        then(portfolioRepo).should().delete(portfolio);
    }

    @Test
    void delete_portfolioWithTrades_throws409() {
        Portfolio portfolio = buildPortfolio();
        given(portfolioRepo.findByIdAndUserId(PORTFOLIO_ID, USER_ID))
                .willReturn(Optional.of(portfolio));
        given(tradeRepo.existsByPortfolioId(PORTFOLIO_ID)).willReturn(true);

        assertThatThrownBy(() -> service.delete(PORTFOLIO_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessage("PORTFOLIO_HAS_TRADES");

        then(portfolioRepo).should(never()).delete(any());
    }

    @Test
    void delete_unknownPortfolio_throws404() {
        given(portfolioRepo.findByIdAndUserId(PORTFOLIO_ID, USER_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(PORTFOLIO_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── archive ─────────────────────────────────────────────────────────────

    @Test
    void archive_setsArchivedTrue() {
        Portfolio portfolio = buildPortfolio();
        given(portfolioRepo.findByIdAndUserId(PORTFOLIO_ID, USER_ID))
                .willReturn(Optional.of(portfolio));
        given(portfolioRepo.save(any())).willAnswer(inv -> inv.getArgument(0));

        var response = service.archive(PORTFOLIO_ID);
        assertThat(response.isArchived()).isTrue();
    }

    @Test
    void archive_archivedPortfolioDoesNotAppearInList() {
        // After archiving, list should not include it (uses findByUserIdAndArchivedFalse)
        Page<Portfolio> emptyPage = new PageImpl<>(java.util.List.of());
        given(portfolioRepo.findByUserIdAndArchivedFalse(eq(USER_ID), any(Pageable.class)))
                .willReturn(emptyPage);

        var page = service.list(1, 20);
        assertThat(page.items()).isEmpty();
    }

    @Test
    void archive_unknownPortfolio_throws404() {
        given(portfolioRepo.findByIdAndUserId(PORTFOLIO_ID, USER_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.archive(PORTFOLIO_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static AppUser buildUser() {
        AppUser user = mock(AppUser.class);
        given(user.getId()).willReturn(USER_ID);
        return user;
    }

    private Portfolio buildPortfolio() {
        AppUser user = buildUser();
        return new Portfolio(PORTFOLIO_ID, user, "My Portfolio", "desc", "USD");
    }
}
