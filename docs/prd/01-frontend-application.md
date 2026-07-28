<!-- generated-by: gsd-doc-writer -->

# PRD 1: Frontend Application and Client Dashboard

## 1. Ownership

| Item | Details |
|---|---|
| Primary owner | Member 1 (replace with a name before submission) |
| Scope | All frontend pages, components, API clients, and frontend tests |
| Out of scope | Spring Boot APIs, worker jobs, MySQL tables, and backend business tests |
| Primary technologies | React, TypeScript, Vite, TanStack Query, Chart.js |
| Interface contracts | [API Reference](../API.md) / [OpenAPI Specification](../openapi.yaml) |
| Status | Pending implementation |

Member 1 is the sole frontend owner. Members 2–5 deliver stable backend interfaces and example data; Member 1 does not participate in backend or database implementation.

## 2. Product Goal

Deliver a responsive, client-ready portfolio dashboard that lets users create and switch portfolios, record trades, inspect holdings, trigger market-data synchronization, and understand market value, profit and loss, allocation, and historical performance through interactive charts.

## 3. Page Scope

### 3.1 Application Shell

- Desktop side navigation and expandable mobile navigation.
- Pages: Dashboard, Portfolios, Holdings, Transactions, and Data Status.
- The current portfolio selector and synchronization status remain visible.
- Consistent colors, typography, spacing, currency formatting, and chart palette.

### 3.2 Portfolio Management

- Portfolio list, creation, rename, and archive operations.
- First-portfolio guidance when no portfolio exists.
- Store the current portfolio ID in the URL so selection survives a refresh.

### 3.3 Trading and Holdings

- Stock and ETF search.
- A compact buy/sell ticket that selects a real available trading date and exact
  market minute, then shows the stored one-minute close as a read-only unit price.
- An optional editable fee that defaults to `0.00 USD`; clients never submit an
  arbitrary price or currency.
- Current holdings table and transaction history.
- Clear errors for overselling, duplicate submission, and invalid fields.

### 3.4 Market-Data Status

- Data Status page and manual synchronization button.
- Display provider, run status, success/failure counts, and last synchronization time.
- Display `FRESH`, `STALE`, and `UNAVAILABLE`.

### 3.5 Valuation and Analytics

- Summary cards for total market value, total cost, unrealized profit and loss, and return.
- Asset-allocation doughnut chart.
- Historical valuation line chart.
- Identify holdings without prices separately; never represent them as zero-priced.

## 4. API Integration Ownership

| Backend owner | Interfaces consumed by the frontend |
|---|---|
| Member 2 | Portfolio list, create, update, delete, and archive |
| Member 3 | Instrument search, transaction creation, transaction history, and current positions |
| Member 4 | Manual synchronization, synchronization status, and latest prices |
| Member 5 | Dashboard, performance, and health status |

Integration rules:

- Generate TypeScript types from [openapi.yaml](../openapi.yaml), or validate them for contract consistency.
- Receive amounts, prices, quantities, and percentages as decimal strings.
- The frontend formats values for display only and does not redefine backend financial formulas.
- Read `code`, `message`, `fieldErrors`, and `requestId` consistently from API errors.
- Query keys must include `portfolioId`; cancel requests for the previous portfolio after switching.

## 5. State and Interaction

### 5.1 Loading

- Show fixed-height skeletons immediately on initial load.
- Avoid significant layout shifts while charts and tables load.
- Preserve the last successful data during background refreshes.

### 5.2 Empty States

- No portfolio: guide the user to create the first portfolio.
- No holdings: guide the user to search for a stock or ETF and record a buy.
- No historical snapshots: explain that market-data synchronization must complete first.

### 5.3 Errors

- Display an understandable message, a retry button, and the `requestId`.
- Place field errors next to the corresponding form controls.
- Never expose Java stack traces, SQL, secrets, or full provider responses.

### 5.4 Market-Data Status

- `FRESH`: display the price trading date and source normally.
- `STALE`: show a non-blocking warning and continue using the last price.
- `UNAVAILABLE`: display “Price unavailable” and explain that the holding is excluded from market value and allocation.

## 6. Responsive Design and Accessibility

- No page-level horizontal scrolling at the 390 px, 1024 px, or 1440 px reference widths.
- Primary touch targets are at least 44×44 CSS pixels.
- Every interactive element has a visible focus indicator.
- Charts have table or text equivalents.
- Positive and negative profit/loss do not rely on red and green alone.
- Respect the operating system's reduced-motion preference.

## 7. Frontend Tests

Member 1 must independently complete:

### Unit and Component Tests

- Amount, quantity, percentage, and date formatting.
- Portfolio selector and management forms.
- Instrument search and transaction form.
- Tradable-date loading, read-only resolved unit price, and unavailable-price behavior.
- Summary cards, holdings table, doughnut chart, and line chart.
- Synchronization status and error components.

### API Integration Tests

- Use Mock Service Worker or an equivalent tool to cover every interface example.
- Cover successful, empty, validation-failure, 404, 409, stale, and unavailable-price states.
- Verify request bodies against [API.md](../API.md).

### Frontend End-to-End Tests

- Create portfolio → search stock/ETF → select a tradable date/real close → buy → inspect charts.
- Display duplicate-submission and oversell errors.
- Manual synchronization and stale status after provider failure.
- Ensure data never leaks across portfolio switches.

### Accessibility and Visual Checks

- Run automated accessibility scans on critical pages.
- Manually verify keyboard navigation.
- Review screenshots at the three reference widths.

## 8. Acceptance Criteria

### AC-FE-01: Complete Workflow

Given an available backend, after a user creates a portfolio, buys an instrument, and synchronizes data:

- Dashboard displays the real response summary, holdings, allocation, and price date.
- Pages contain no hard-coded business data.
- Refreshing the browser preserves the current portfolio.

### AC-FE-02: API Errors

Given a standard API error response:

- Form field errors appear at the correct controls.
- The page displays the backend `message` and `requestId`.
- No internal exception information is shown.

### AC-FE-03: Data Consistency

Given a dashboard response:

- Summary cards, holdings table, and allocation chart use the same response.
- The frontend does not recalculate average cost or authoritative profit/loss.
- Holdings without prices do not appear in the allocation chart.

### AC-FE-04: Responsive Layout

Given a 390 px viewport:

- Navigation, summaries, forms, and holding information remain operable.
- There is no page-level horizontal scrolling or overlapping content.

## 9. Deliverables

- React/Vite frontend application.
- Pages, components, design tokens, and API client.
- TypeScript types generated from or validated against OpenAPI.
- Frontend unit, component, mocked integration, and end-to-end tests.
- Demonstration states for successful, empty, stale, and error scenarios.

## 10. Demonstration Responsibility

Member 1 demonstrates all frontend operations and the client experience, and explains how the frontend strictly consumes the four backend modules according to the OpenAPI contract.

## 11. Definition of Done

- Every page is connected to the real API.
- No primary business workflow relies on static sample data.
- Frontend tests pass.
- Accessibility and responsive acceptance checks pass.
- API types match the OpenAPI specification.
