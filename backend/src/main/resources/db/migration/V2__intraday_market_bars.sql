CREATE TABLE IF NOT EXISTS market_intraday_bar (
    id              bigint unsigned NOT NULL AUTO_INCREMENT,
    instrument_id   char(36) NOT NULL,
    interval_code   varchar(8) NOT NULL,
    bar_timestamp   datetime(6) NOT NULL,
    open_price      decimal(20, 8) NOT NULL,
    high_price      decimal(20, 8) NOT NULL,
    low_price       decimal(20, 8) NOT NULL,
    close_price     decimal(20, 8) NOT NULL,
    volume          bigint unsigned NULL,
    currency        char(3) NOT NULL,
    source          varchar(40) NOT NULL,
    fetched_at      datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_intraday_bar_instrument
        FOREIGN KEY (instrument_id)
        REFERENCES instrument (id)
        ON DELETE CASCADE,
    CONSTRAINT uq_intraday_bar_identity
        UNIQUE (instrument_id, interval_code, bar_timestamp, source),
    CONSTRAINT intraday_bar_prices_positive
        CHECK (
            open_price > 0
            AND high_price > 0
            AND low_price > 0
            AND close_price > 0
            AND high_price >= low_price
        ),
    KEY ix_intraday_bar_lookup
        (instrument_id, interval_code, bar_timestamp DESC)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Provider-normalized intraday OHLCV bars in UTC';
