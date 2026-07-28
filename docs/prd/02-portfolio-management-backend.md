# PRD 2: Portfolio Management Backend

## Goal

Provide the ownership boundary and portfolio lifecycle used by every other module.

## Scope

- List, create, read, update, delete, and archive portfolios.
- Use the server-side demo user for the MVP.
- Enforce case-insensitive uniqueness for active portfolio names per user.
- Return 404 for resources outside the current user boundary.
- Allow hard deletion only when a portfolio has no transactions.

## Data delivery

The module owns the `users` and `portfolios` schema, foreign keys, indexes, seed
data, and migration behavior. Archived records remain available for audit but are
excluded from default queries.

## Contract and errors

The module implements the Portfolios OpenAPI tag and documents success, validation,
not-found, and conflict responses. Errors use stable codes including
`PORTFOLIO_NAME_CONFLICT` and `PORTFOLIO_HAS_TRADES`.

## Tests

Tests cover creation, rename, archive, deletion rules, name conflicts, ownership
isolation, database constraints, pagination, and runtime OpenAPI compatibility.

## Acceptance criteria

- A valid portfolio is persisted and returned through the API.
- Duplicate active names are rejected within one user boundary.
- One user cannot observe another user's portfolio.
- Runtime documentation includes schemas, examples, and standard errors.
