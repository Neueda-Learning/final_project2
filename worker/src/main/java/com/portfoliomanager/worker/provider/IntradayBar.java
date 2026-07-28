package com.portfoliomanager.worker.provider;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record IntradayBar(
        String symbol,
        String interval,
        LocalDateTime timestamp,
        BigDecimal openPrice,
        BigDecimal highPrice,
        BigDecimal lowPrice,
        BigDecimal closePrice,
        Long volume,
        String currency,
        String source) {}
