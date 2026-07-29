package com.portfoliomanager.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfoliomanager.domain.model.PortfolioPosition;
import com.portfoliomanager.domain.model.TradeTransaction;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class TradingRepositoryPerformanceTest {

    private static final String PORTFOLIO_ID = "portfolio-1";
    private static final String USER_ID = "user-1";

    @Autowired private PortfolioPositionRepository positions;
    @Autowired private TradeTransactionRepository transactions;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        jdbc.update(
                """
                INSERT INTO app_user
                    (id, email, display_name, is_active, created_at, updated_at)
                VALUES (?, ?, ?, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                USER_ID,
                "performance@example.com",
                "Performance Test");
        jdbc.update(
                """
                INSERT INTO portfolio
                    (id, user_id, name, base_currency, is_archived, created_at, updated_at)
                VALUES (?, ?, ?, 'USD', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                PORTFOLIO_ID,
                USER_ID,
                "Performance Portfolio");

        for (int index = 1; index <= 3; index++) {
            String instrumentId = "instrument-" + index;
            jdbc.update(
                    """
                    INSERT INTO instrument
                        (id, symbol, name, asset_type, exchange_code, currency,
                         is_active, created_at, updated_at)
                    VALUES (?, ?, ?, 'STOCK', 'NASDAQ', 'USD',
                            true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    instrumentId,
                    "SYM" + index,
                    "Instrument " + index);

            if (index <= 2) {
                jdbc.update(
                        """
                        INSERT INTO portfolio_position
                            (portfolio_id, instrument_id, quantity, average_cost,
                             realized_pnl, version, opened_at, updated_at)
                        VALUES (?, ?, ?, 100, 0, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                        PORTFOLIO_ID,
                        instrumentId,
                        index);
            }

            jdbc.update(
                    """
                    INSERT INTO trade_transaction
                        (id, portfolio_id, instrument_id, side, quantity, unit_price,
                         fee_amount, currency, executed_at, idempotency_key, created_at)
                    VALUES (?, ?, ?, 'BUY', 1, 100, 0, 'USD',
                            CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP)
                    """,
                    "transaction-" + index,
                    PORTFOLIO_ID,
                    instrumentId,
                    "key-" + index);
        }

        entityManager.clear();
        statistics.clear();
    }

    @Test
    void positionsLoadAllInstrumentFieldsWithOneStatement() {
        List<PortfolioPosition> result = positions.findByPortfolioId(PORTFOLIO_ID);

        assertThat(result)
                .extracting(position -> position.getInstrument().getSymbol())
                .containsExactly("SYM2", "SYM1");
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void transactionPageUsesConstantStatementsWhenInstrumentFieldsAreRead() {
        Page<TradeTransaction> result =
                transactions.findByPortfolioIdOrderByExecutedAtDesc(
                        PORTFOLIO_ID, PageRequest.of(0, 2));

        assertThat(result.getContent())
                .extracting(transaction -> transaction.getInstrument().getSymbol())
                .hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }

    @Test
    void idempotentReplayFetchesItsInstrumentWithOneStatement() {
        TradeTransaction result =
                transactions
                        .findByPortfolioIdAndIdempotencyKey(PORTFOLIO_ID, "key-1")
                        .orElseThrow();

        assertThat(result.getInstrument().getSymbol()).isEqualTo("SYM1");
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }
}
