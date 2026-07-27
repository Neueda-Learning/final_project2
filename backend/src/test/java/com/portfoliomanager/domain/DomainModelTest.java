package com.portfoliomanager.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfoliomanager.api.ApiModels.TransactionCreateRequest;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class DomainModelTest {

    @Test
    void rejectsNonPositiveTradeQuantity() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var request = new TransactionCreateRequest(
                    "instrument-id",
                    TradeSide.BUY,
                    BigDecimal.ZERO,
                    new BigDecimal("100.00"),
                    BigDecimal.ZERO,
                    "USD",
                    LocalDateTime.now(),
                    null);

            assertThat(validatorFactory.getValidator().validate(request))
                    .extracting("propertyPath")
                    .map(Object::toString)
                    .contains("quantity");
        }
    }
}
