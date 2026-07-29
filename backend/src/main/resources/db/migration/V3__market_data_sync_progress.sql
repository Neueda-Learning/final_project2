ALTER TABLE market_data_sync_run
    ADD COLUMN stage enum(
        'QUEUED',
        'FETCHING_MARKET_DATA',
        'REFRESHING_CURRENT_VALUATIONS',
        'REBUILDING_HISTORICAL_VALUATIONS',
        'COMPLETED'
    ) NOT NULL DEFAULT 'QUEUED'
    AFTER status;

UPDATE market_data_sync_run
SET stage = CASE
    WHEN status = 'RUNNING' THEN 'FETCHING_MARKET_DATA'
    ELSE 'COMPLETED'
END;
