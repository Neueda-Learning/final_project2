package com.portfoliomanager.worker.provider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record DailyPrice(
        String symbol,
        LocalDate priceDate,
        BigDecimal openPrice,
        BigDecimal highPrice,
        BigDecimal lowPrice,
        BigDecimal closePrice,
        BigDecimal adjustedClose,
        Long volume,
        String currency,
        String source,
        LocalDateTime sourceTimestamp) {}
