package com.portfoliomanager.worker.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DailyPriceTest {

    @Test
    void keepsFinancialValuesAsBigDecimal() {
        var price = new DailyPrice(
                "AAPL",
                LocalDate.of(2026, 7, 27),
                null,
                null,
                null,
                new BigDecimal("215.12000000"),
                null,
                100L,
                "USD",
                "test",
                null);

        assertThat(price.closePrice()).isEqualByComparingTo("215.12000000");
    }
}
