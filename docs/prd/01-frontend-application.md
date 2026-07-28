<!-- generated-by: gsd-doc-writer -->

# PRD 1: Frontend Application and Client Dashboard

## Ownership

The frontend owner delivers every client page, shared component, API adapter, and
frontend test. Backend services, database tables, and backend business tests remain
outside this scope.

## Product goal

Deliver a responsive, client-ready portfolio experience for creating and switching
portfolios, recording trades, reviewing holdings, synchronizing prices, and
understanding valuation, P&L, allocation, and historical performance.

## Page scope

- A shared application shell with Dashboard, Portfolios, Holdings, Transactions,
  and Data Status navigation.
- Portfolio creation, rename, selection, and archive flows.
- Stock and ETF search, purchase and sale forms, current holdings, and history.
- Synchronization status with fresh, stale, and unavailable price states.
- Summary metrics, allocation visualization, and valuation history.
- English as the default interface language with an English/Chinese switch.

## Integration rules

- Types must remain compatible with [openapi.yaml](../openapi.yaml).
- Financial values arrive as decimal strings and are formatted only for display.
- Backend formulas remain authoritative.
- Errors consume `code`, `message`, `fieldErrors`, and `requestId`.
- Query keys include `portfolioId` to prevent cross-portfolio data reuse.

## Experience requirements

- Stable skeletons prevent large layout shifts.
- Empty states explain the next useful action.
- Errors remain actionable and never expose internal implementation details.
- Missing prices are not displayed as zero.
- Baseline widths are 390, 1024, and 1440 CSS pixels.
- Primary controls are at least 44 by 44 CSS pixels.
- Focus is visible, reduced-motion preferences are respected, and charts have
  equivalent textual data.

## Testing

Tests cover formatters, portfolio selection and forms, instrument search, trade
submission, summary and table rendering, charts, synchronization states, and API
errors. End-to-end coverage follows the create portfolio, purchase asset,
synchronize prices, and review dashboard workflow.

## Acceptance criteria

- All pages use live API responses without hard-coded business data.
- Refreshing preserves the current portfolio through the URL.
- Dashboard cards, positions, and allocation use one consistent response.
- Field errors appear beside the relevant control.
- The mobile layout remains operable without page-level horizontal overflow.
