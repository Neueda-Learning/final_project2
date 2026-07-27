# Flyway migrations

Add versioned migrations here using names such as `V2__add_cash_balance.sql`.

`docs/database/schema.sql` is the current baseline and is mounted directly by local
Docker Compose. Before enabling `FLYWAY_ENABLED=true`, promote that baseline to an
approved `V1__baseline.sql` migration and verify it against an empty MySQL 8 database.
