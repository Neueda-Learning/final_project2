USE portfolio_manager;

INSERT IGNORE INTO app_user (id, email, display_name)
VALUES ('11111111-1111-1111-1111-111111111111', 'demo@example.com', 'Demo User');

INSERT IGNORE INTO portfolio (
    id, user_id, name, description, base_currency
)
VALUES (
    '22222222-2222-2222-2222-222222222222',
    '11111111-1111-1111-1111-111111111111',
    'Long-term Growth',
    'Demo portfolio for local development',
    'USD'
);

INSERT IGNORE INTO instrument (
    id, symbol, name, asset_type, exchange_code, currency, provider_symbol
)
VALUES
    ('33333333-3333-3333-3333-333333333333', 'AAPL', 'Apple Inc.', 'STOCK', 'NASDAQ', 'USD', 'AAPL'),
    ('44444444-4444-4444-4444-444444444444', 'VOO', 'Vanguard S&P 500 ETF', 'ETF', 'NYSEARCA', 'USD', 'VOO');
