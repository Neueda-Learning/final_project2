package com.portfoliomanager.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.portfoliomanager.config.WebConfig;
import com.portfoliomanager.repository.PortfolioRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    private static final String USER_ID    = "11111111-1111-1111-1111-111111111111";
    private static final String PORTFOLIO_ID = "22222222-2222-2222-2222-222222222222";

    @Mock private JdbcTemplate jdbc;
    @Mock private PortfolioRepository portfolios;
    @Mock private MarketCalendarService marketCalendar;

    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        WebConfig config = mock(WebConfig.class);
        lenient().when(config.getDemoUserId()).thenReturn(USER_ID);
        service = new AnalyticsService(jdbc, portfolios, config, marketCalendar);
    }

    // ── performance ────────────────────────────────────────────────────────

    @Test
    void performance_fromAfterTo_throwsInvalidDateRange() {
        assertThatThrownBy(() ->
                service.performance(
                        PORTFOLIO_ID,
                        LocalDate.of(2026, 7, 10),
                        LocalDate.of(2026, 7, 1)))
                .isInstanceOf(InvalidDateRangeException.class)
                .hasMessageContaining("from must be on or before to");
    }

    @Test
    void performance_sameFromAndTo_doesNotThrowDateRangeError() {
        given(portfolios.findByIdAndUserId(PORTFOLIO_ID, USER_ID))
                .willReturn(Optional.empty());

        // same from == to is valid; the first failure is then portfolio-not-found, not date range
        assertThatThrownBy(() ->
                service.performance(
                        PORTFOLIO_ID,
                        LocalDate.of(2026, 7, 10),
                        LocalDate.of(2026, 7, 10)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("PORTFOLIO_NOT_FOUND");
    }

    @Test
    void performance_unknownPortfolio_throwsResourceNotFound() {
        given(portfolios.findByIdAndUserId(PORTFOLIO_ID, USER_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.performance(PORTFOLIO_ID, null, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("PORTFOLIO_NOT_FOUND");
    }

    // ── dashboard ──────────────────────────────────────────────────────────

    @Test
    void dashboard_unknownPortfolio_throwsResourceNotFound() {
        given(portfolios.findByIdAndUserId(PORTFOLIO_ID, USER_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.dashboard(PORTFOLIO_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("PORTFOLIO_NOT_FOUND");
    }
}
