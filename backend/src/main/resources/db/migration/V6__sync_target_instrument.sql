ALTER TABLE market_data_sync_run
    ADD COLUMN target_instrument_id char(36) NULL AFTER triggered_by;

ALTER TABLE market_data_sync_run
    ADD CONSTRAINT fk_sync_target_instrument
        FOREIGN KEY (target_instrument_id)
        REFERENCES instrument (id)
        ON DELETE SET NULL;

ALTER TABLE market_data_sync_run
    ADD KEY ix_sync_target_instrument (target_instrument_id);
