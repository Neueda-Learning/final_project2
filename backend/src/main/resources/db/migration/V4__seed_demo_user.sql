-- Ensure the default demo user exists for local and standalone backend runs.
INSERT IGNORE INTO app_user (id, email, display_name, is_active)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    'demo@example.com',
    'Demo User',
    true
);