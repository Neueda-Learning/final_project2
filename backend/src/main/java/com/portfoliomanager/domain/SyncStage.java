package com.portfoliomanager.domain;

public enum SyncStage {
    QUEUED,
    FETCHING_MARKET_DATA,
    REFRESHING_CURRENT_VALUATIONS,
    REBUILDING_HISTORICAL_VALUATIONS,
    COMPLETED
}
