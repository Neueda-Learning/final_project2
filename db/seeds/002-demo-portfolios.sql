USE portfolio_manager;

-- Populate the extended demo catalog without changing portfolios that already
-- contain transactions or positions. This script is safe to run repeatedly.

CREATE TEMPORARY TABLE demo_portfolio_definition (
    id char(36) NOT NULL,
    name varchar(120) NOT NULL,
    description varchar(500) NOT NULL,
    PRIMARY KEY (name)
);

INSERT INTO demo_portfolio_definition (id, name, description)
VALUES
    ('66666666-0000-0000-0000-000000000001', 'Tech Growth Fund', 'Focused on AI, cloud, and large-cap technology'),
    ('66666666-0000-0000-0000-000000000002', 'Dividend Income', 'Dividend-oriented equities and income funds'),
    ('66666666-0000-0000-0000-000000000003', 'Index Core', 'Low-cost broad-market index allocation'),
    ('66666666-0000-0000-0000-000000000004', 'Emerging Markets', 'Diversified emerging-market equity exposure'),
    ('66666666-0000-0000-0000-000000000005', 'Healthcare Portfolio', 'Healthcare and biotechnology sector exposure'),
    ('66666666-0000-0000-0000-000000000006', 'ESG Clean Energy', 'Clean-energy and transition-economy equities'),
    ('66666666-0000-0000-0000-000000000007', 'Value Investing', 'Quality companies selected with a value discipline'),
    ('66666666-0000-0000-0000-000000000008', 'Small Cap Growth', 'Growth-oriented US small-cap allocation'),
    ('66666666-0000-0000-0000-000000000009', 'Real Estate REIT', 'Listed real estate and rental-income exposure'),
    ('66666666-0000-0000-0000-000000000010', 'Global Diversified', 'Diversified allocation across US and international markets'),
    ('66666666-0000-0000-0000-000000000011', 'Momentum Strategy', 'Systematic equity momentum allocation'),
    ('66666666-0000-0000-0000-000000000012', 'Semiconductor Focus', 'Semiconductor design and manufacturing exposure'),
    ('66666666-0000-0000-0000-000000000013', 'Financial Sector', 'Banks, insurers, and diversified financial companies'),
    ('66666666-0000-0000-0000-000000000014', 'Consumer Staples', 'Defensive consumer-staples companies'),
    ('66666666-0000-0000-0000-000000000015', 'Cloud & SaaS', 'Cloud infrastructure and software companies'),
    ('66666666-0000-0000-0000-000000000016', 'Biotech Speculative', 'Higher-volatility biotechnology allocation'),
    ('66666666-0000-0000-0000-000000000017', 'Balanced 60/40', 'Balanced equity and investment-grade bond allocation'),
    ('66666666-0000-0000-0000-000000000018', 'Crypto-Adjacent', 'Regulated Bitcoin ETF and digital-asset equity exposure'),
    ('66666666-0000-0000-0000-000000000019', 'Retirement IRA', 'Long-horizon diversified retirement allocation'),
    ('66666666-0000-0000-0000-000000000020', 'Swing Trading', 'Liquid growth assets used for medium-term trend trades'),
    ('66666666-0000-0000-0000-000000000021', '2022 Bear Strategy', 'Defensive equity hedge and gold allocation'),
    ('66666666-0000-0000-0000-000000000022', 'IPO Watchlist 2023', 'Publicly traded recent-listing and growth-company basket'),
    ('66666666-0000-0000-0000-000000000023', 'My Test Portfolio', 'Small diversified portfolio for functional testing');

INSERT IGNORE INTO portfolio (
    id, user_id, name, description, base_currency
)
SELECT
    definition.id,
    '11111111-1111-1111-1111-111111111111',
    definition.name,
    definition.description,
    'USD'
FROM demo_portfolio_definition AS definition;

CREATE TEMPORARY TABLE demo_instrument_definition (
    id char(36) NOT NULL,
    symbol varchar(32) NOT NULL,
    name varchar(200) NOT NULL,
    asset_type varchar(10) NOT NULL,
    exchange_code varchar(32) NOT NULL,
    provider_symbol varchar(64) NOT NULL,
    reference_price decimal(20, 8) NOT NULL,
    PRIMARY KEY (symbol)
);

INSERT INTO demo_instrument_definition (
    id, symbol, name, asset_type, exchange_code, provider_symbol, reference_price
)
VALUES
    ('55555555-0000-0000-0000-000000000001', 'NVDA', 'NVIDIA Corporation', 'STOCK', 'NASDAQ', 'NVDA', 180.00),
    ('55555555-0000-0000-0000-000000000002', 'MSFT', 'Microsoft Corporation', 'STOCK', 'NASDAQ', 'MSFT', 520.00),
    ('55555555-0000-0000-0000-000000000003', 'SCHD', 'Schwab US Dividend Equity ETF', 'ETF', 'NYSEARCA', 'SCHD', 28.00),
    ('55555555-0000-0000-0000-000000000004', 'JNJ', 'Johnson & Johnson', 'STOCK', 'NYSE', 'JNJ', 175.00),
    ('55555555-0000-0000-0000-000000000005', 'VTI', 'Vanguard Total Stock Market ETF', 'ETF', 'NYSEARCA', 'VTI', 330.00),
    ('55555555-0000-0000-0000-000000000006', 'VOO', 'Vanguard S&P 500 ETF', 'ETF', 'NYSEARCA', 'VOO', 620.00),
    ('55555555-0000-0000-0000-000000000007', 'VWO', 'Vanguard FTSE Emerging Markets ETF', 'ETF', 'NYSEARCA', 'VWO', 55.00),
    ('55555555-0000-0000-0000-000000000008', 'IEMG', 'iShares Core MSCI Emerging Markets ETF', 'ETF', 'NYSEARCA', 'IEMG', 68.00),
    ('55555555-0000-0000-0000-000000000009', 'XLV', 'Health Care Select Sector SPDR Fund', 'ETF', 'NYSEARCA', 'XLV', 145.00),
    ('55555555-0000-0000-0000-000000000010', 'IBB', 'iShares Biotechnology ETF', 'ETF', 'NASDAQ', 'IBB', 135.00),
    ('55555555-0000-0000-0000-000000000011', 'ICLN', 'iShares Global Clean Energy ETF', 'ETF', 'NASDAQ', 'ICLN', 15.00),
    ('55555555-0000-0000-0000-000000000012', 'TAN', 'Invesco Solar ETF', 'ETF', 'NYSEARCA', 'TAN', 44.00),
    ('55555555-0000-0000-0000-000000000013', 'BRK.B', 'Berkshire Hathaway Class B', 'STOCK', 'NYSE', 'BRK.B', 500.00),
    ('55555555-0000-0000-0000-000000000014', 'JPM', 'JPMorgan Chase & Co.', 'STOCK', 'NYSE', 'JPM', 300.00),
    ('55555555-0000-0000-0000-000000000015', 'IWM', 'iShares Russell 2000 ETF', 'ETF', 'NYSEARCA', 'IWM', 235.00),
    ('55555555-0000-0000-0000-000000000016', 'VB', 'Vanguard Small-Cap ETF', 'ETF', 'NYSEARCA', 'VB', 260.00),
    ('55555555-0000-0000-0000-000000000017', 'VNQ', 'Vanguard Real Estate ETF', 'ETF', 'NYSEARCA', 'VNQ', 93.00),
    ('55555555-0000-0000-0000-000000000018', 'O', 'Realty Income Corporation', 'STOCK', 'NYSE', 'O', 58.00),
    ('55555555-0000-0000-0000-000000000019', 'VT', 'Vanguard Total World Stock ETF', 'ETF', 'NYSEARCA', 'VT', 135.00),
    ('55555555-0000-0000-0000-000000000020', 'VXUS', 'Vanguard Total International Stock ETF', 'ETF', 'NASDAQ', 'VXUS', 75.00),
    ('55555555-0000-0000-0000-000000000021', 'MTUM', 'iShares MSCI USA Momentum Factor ETF', 'ETF', 'CBOE', 'MTUM', 240.00),
    ('55555555-0000-0000-0000-000000000022', 'QQQ', 'Invesco QQQ Trust', 'ETF', 'NASDAQ', 'QQQ', 570.00),
    ('55555555-0000-0000-0000-000000000023', 'SOXX', 'iShares Semiconductor ETF', 'ETF', 'NASDAQ', 'SOXX', 315.00),
    ('55555555-0000-0000-0000-000000000024', 'SMH', 'VanEck Semiconductor ETF', 'ETF', 'NASDAQ', 'SMH', 330.00),
    ('55555555-0000-0000-0000-000000000025', 'XLF', 'Financial Select Sector SPDR Fund', 'ETF', 'NYSEARCA', 'XLF', 55.00),
    ('55555555-0000-0000-0000-000000000026', 'XLP', 'Consumer Staples Select Sector SPDR Fund', 'ETF', 'NYSEARCA', 'XLP', 82.00),
    ('55555555-0000-0000-0000-000000000027', 'PG', 'Procter & Gamble Co.', 'STOCK', 'NYSE', 'PG', 160.00),
    ('55555555-0000-0000-0000-000000000028', 'SKYY', 'First Trust Cloud Computing ETF', 'ETF', 'NASDAQ', 'SKYY', 130.00),
    ('55555555-0000-0000-0000-000000000029', 'XBI', 'SPDR S&P Biotech ETF', 'ETF', 'NYSEARCA', 'XBI', 100.00),
    ('55555555-0000-0000-0000-000000000030', 'BND', 'Vanguard Total Bond Market ETF', 'ETF', 'NASDAQ', 'BND', 75.00),
    ('55555555-0000-0000-0000-000000000031', 'IBIT', 'iShares Bitcoin Trust ETF', 'ETF', 'NASDAQ', 'IBIT', 70.00),
    ('55555555-0000-0000-0000-000000000032', 'COIN', 'Coinbase Global Inc.', 'STOCK', 'NASDAQ', 'COIN', 350.00),
    ('55555555-0000-0000-0000-000000000033', 'TSLA', 'Tesla Inc.', 'STOCK', 'NASDAQ', 'TSLA', 330.00),
    ('55555555-0000-0000-0000-000000000034', 'SH', 'ProShares Short S&P 500', 'ETF', 'NYSEARCA', 'SH', 36.00),
    ('55555555-0000-0000-0000-000000000035', 'GLD', 'SPDR Gold Shares', 'ETF', 'NYSEARCA', 'GLD', 310.00),
    ('55555555-0000-0000-0000-000000000036', 'IPO', 'Renaissance IPO ETF', 'ETF', 'NYSEARCA', 'IPO', 45.00),
    ('55555555-0000-0000-0000-000000000037', 'AAPL', 'Apple Inc.', 'STOCK', 'NASDAQ', 'AAPL', 215.00);

INSERT IGNORE INTO instrument (
    id, symbol, name, asset_type, exchange_code, currency, provider_symbol
)
SELECT
    definition.id,
    definition.symbol,
    definition.name,
    definition.asset_type,
    definition.exchange_code,
    'USD',
    definition.provider_symbol
FROM demo_instrument_definition AS definition;

CREATE TEMPORARY TABLE demo_allocation (
    portfolio_name varchar(120) NOT NULL,
    symbol varchar(32) NOT NULL,
    quantity decimal(28, 8) NOT NULL,
    PRIMARY KEY (portfolio_name, symbol)
);

INSERT INTO demo_allocation (portfolio_name, symbol, quantity)
VALUES
    ('Tech Growth Fund', 'NVDA', 80), ('Tech Growth Fund', 'MSFT', 25),
    ('Dividend Income', 'SCHD', 500), ('Dividend Income', 'JNJ', 80),
    ('Index Core', 'VTI', 60), ('Index Core', 'VOO', 30),
    ('Emerging Markets', 'VWO', 300), ('Emerging Markets', 'IEMG', 250),
    ('Healthcare Portfolio', 'XLV', 100), ('Healthcare Portfolio', 'IBB', 100),
    ('ESG Clean Energy', 'ICLN', 800), ('ESG Clean Energy', 'TAN', 250),
    ('Value Investing', 'BRK.B', 30), ('Value Investing', 'JPM', 50),
    ('Small Cap Growth', 'IWM', 60), ('Small Cap Growth', 'VB', 60),
    ('Real Estate REIT', 'VNQ', 150), ('Real Estate REIT', 'O', 250),
    ('Global Diversified', 'VT', 120), ('Global Diversified', 'VXUS', 200),
    ('Momentum Strategy', 'MTUM', 60), ('Momentum Strategy', 'QQQ', 25),
    ('Semiconductor Focus', 'SOXX', 45), ('Semiconductor Focus', 'SMH', 45),
    ('Financial Sector', 'XLF', 250), ('Financial Sector', 'JPM', 45),
    ('Consumer Staples', 'XLP', 175), ('Consumer Staples', 'PG', 85),
    ('Cloud & SaaS', 'SKYY', 110), ('Cloud & SaaS', 'MSFT', 28),
    ('Biotech Speculative', 'XBI', 140), ('Biotech Speculative', 'IBB', 100),
    ('Balanced 60/40', 'VTI', 55), ('Balanced 60/40', 'BND', 190),
    ('Crypto-Adjacent', 'IBIT', 180), ('Crypto-Adjacent', 'COIN', 35),
    ('Retirement IRA', 'VTI', 55), ('Retirement IRA', 'VOO', 30),
    ('Swing Trading', 'QQQ', 25), ('Swing Trading', 'TSLA', 40),
    ('2022 Bear Strategy', 'SH', 350), ('2022 Bear Strategy', 'GLD', 40),
    ('IPO Watchlist 2023', 'IPO', 300), ('IPO Watchlist 2023', 'QQQ', 22),
    ('My Test Portfolio', 'AAPL', 50), ('My Test Portfolio', 'VOO', 15);

CREATE TEMPORARY TABLE demo_empty_portfolio (
    portfolio_id char(36) NOT NULL,
    portfolio_name varchar(120) NOT NULL,
    PRIMARY KEY (portfolio_id)
);

INSERT INTO demo_empty_portfolio (portfolio_id, portfolio_name)
SELECT portfolio.id, portfolio.name
FROM portfolio
JOIN demo_portfolio_definition AS definition
  ON definition.name = portfolio.name
WHERE portfolio.is_archived = false
  AND portfolio.id <> '22222222-2222-2222-2222-222222222222'
  AND NOT EXISTS (
      SELECT 1
      FROM trade_transaction
      WHERE trade_transaction.portfolio_id = portfolio.id
  )
  AND NOT EXISTS (
      SELECT 1
      FROM portfolio_position
      WHERE portfolio_position.portfolio_id = portfolio.id
  );

INSERT IGNORE INTO trade_transaction (
    id, portfolio_id, instrument_id, side, quantity, unit_price,
    fee_amount, currency, executed_at, idempotency_key, note
)
SELECT
    UUID(),
    target.portfolio_id,
    instrument.id,
    'BUY',
    allocation.quantity,
    definition.reference_price,
    0.00,
    'USD',
    '2026-06-30 14:30:00',
    CONCAT('demo-seed-', LOWER(REPLACE(allocation.symbol, '.', '-'))),
    'Initial diversified demo allocation'
FROM demo_empty_portfolio AS target
JOIN demo_allocation AS allocation
  ON allocation.portfolio_name = target.portfolio_name
JOIN demo_instrument_definition AS definition
  ON definition.symbol = allocation.symbol
JOIN instrument
  ON instrument.symbol = allocation.symbol
 AND instrument.exchange_code = definition.exchange_code;

INSERT INTO portfolio_position (
    portfolio_id, instrument_id, quantity, average_cost, realized_pnl,
    version, opened_at, updated_at
)
SELECT
    target.portfolio_id,
    instrument.id,
    allocation.quantity,
    definition.reference_price,
    0,
    1,
    '2026-06-30 14:30:00',
    CURRENT_TIMESTAMP(6)
FROM demo_empty_portfolio AS target
JOIN demo_allocation AS allocation
  ON allocation.portfolio_name = target.portfolio_name
JOIN demo_instrument_definition AS definition
  ON definition.symbol = allocation.symbol
JOIN instrument
  ON instrument.symbol = allocation.symbol
 AND instrument.exchange_code = definition.exchange_code
ON DUPLICATE KEY UPDATE portfolio_id = portfolio_position.portfolio_id;

DROP TEMPORARY TABLE demo_empty_portfolio;
DROP TEMPORARY TABLE demo_allocation;
DROP TEMPORARY TABLE demo_instrument_definition;
DROP TEMPORARY TABLE demo_portfolio_definition;
