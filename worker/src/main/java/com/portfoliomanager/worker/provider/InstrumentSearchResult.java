package com.portfoliomanager.worker.provider;

public record InstrumentSearchResult(
        String symbol,
        String name,
        String exchange,
        String currency,
        String instrumentType) {}
