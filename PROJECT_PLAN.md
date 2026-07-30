# Project Plan and Progress Tracker

Last Updated: 2026-07-30
Owner: Team Portfolio Manager
Status Scale: `Not Started` | `In Progress` | `Done` | `Blocked`

## 1. Objective

This document tracks delivery plan and implementation progress for the Portfolio Manager MVP across frontend, backend, worker, database, integration, and documentation. It is evidence-based and aligned with repository history.

## 2. Scope

In Scope
- Portfolio lifecycle management
- Trade entry and position updates
- Market data synchronization and latest pricing
- Dashboard analytics and holdings presentation
- API contract and architecture documentation

Out of Scope (MVP)
- Real trading execution
- Derivatives/options support
- Multi-currency valuation
- Tick-level streaming infrastructure

## 3. Workstreams

- Frontend application: routing shell, dashboard, holdings, trade form, API integration
- Backend core APIs: portfolio, trading, positions, analytics, health
- Market data worker: provider integration, sync job, failure handling
- Database and migrations: schema evolution, constraints, seed compatibility
- Documentation and quality: OpenAPI alignment, architecture, tests, demo readiness

## 4. Milestones from Repository History

Source: `git log --first-parent` on `main` and selected feature commits.

| Date | Commit | Milestone | Impact |
|---|---|---|---|
| 2026-07-27 | 79451de | Scaffold platform | Repository baseline established |
| 2026-07-28 | 2d72b5f | Frontend app shell, pages, API client, tests | MVP frontend structure completed |
| 2026-07-28 | 51be25c / d78adf7 | Backend env configuration fix merged | Runtime config path stabilized |
| 2026-07-28 | 397804a / 8b92623 / a221b62 | PRD4 market-data flow merged | Daily data sync capability added |
| 2026-07-28 | 5e6603f / ce4a74f | Holdings response contract fix merged | Frontend-backend contract alignment improved |
| 2026-07-28 | 2e31768 / f82ced4 | UI redesign and valuation history rebuild | Dashboard quality improved |
| 2026-07-28 | cee2ae3 / 7e6d5be | Trade-time docs and richer demo data | Trade realism and demo readiness improved |
| 2026-07-28 | ca364c8 / e482eca | Minute-level market data support | Data granularity expanded |
| 2026-07-28 | 19cdbad / 36bc781 | Curated holdings universe merged | Better default holdings set |
| 2026-07-28 | eae89ed / 757ec6e / 8170bd4 / 052454e | Intraday trade entry merged | Minute-level execution flow completed |
| 2026-07-29 | ada0b55 / 7f3e394 | Sync progress and return-curve reporting merged | Worker visibility and analytics feedback improved |
| 2026-07-29 | fcad49f | Environment property import support | Configuration portability improved across runtime environments |
| 2026-07-29 | 57bb6f2 | Frontend test step added in CI | Delivery quality gate strengthened |
| 2026-07-30 | 8c7413a | Add assets/dashboard.png screenshot | Visual evidence committed for demo and presentation use |
| 2026-07-30 | 7d2ee01 | Slides update | Presentation deck refreshed on main |
| 2026-07-30 | 7ec5449 (Leon) | test: expand sync progress coverage | Sync-progress test coverage broadened |
| 2026-07-30 | 7356bcc / 62dd9f9 / 903adc1 (Leon) | test: AnalyticsService unit tests, controller coverage, sector edge-cases, helper edge-cases | Analytics test suite significantly expanded |
| 2026-07-30 | 976f78c (feature/Viko) | feat: add unit-price input prefix style | Trade entry UX improved for unit-price field |
| 2026-07-30 | 4b54581 | Merge PR #25 (Leon) + PR #24 (Viko) into main | Expanded test coverage and UX refinement landed on main |

## 5. Delivery Plan with Progress

### 5.1 Frontend

| Task | Status | Evidence | Next Action |
|---|---|---|---|
| App shell and page composition | Done | 2d72b5f, 6d55b4a | Maintain UI consistency |
| Dashboard and valuation display | Done | f82ced4, 8170bd4 | Add regression visual checks |
| Holdings and trade form responsiveness | Done | 757ec6e | Validate edge-case layouts |
| Unit-price input prefix style | Done | 976f78c | Minor UX polish landed |
| Frontend automated tests | Done | 2d72b5f + current `npm test` pass history | Keep tests green after API changes |

### 5.2 Backend API

| Task | Status | Evidence | Next Action |
|---|---|---|---|
| Portfolio/trading core APIs | Done | c7ac1d0, 2703983 | Continue contract hardening |
| Holdings response contract alignment | Done | ce4a74f | Keep OpenAPI/runtime synchronized |
| Environment-based datasource configuration | Done | d78adf7 | Keep env examples current |
| Runtime startup reliability (non-Docker) | In Progress | recent startup troubleshooting sessions | Stabilize single-command startup behavior across environments |
| Analytics service unit tests | Done | 7356bcc | AnalyticsService coverage expanded with new unit test suite |

### 5.3 Market Data Worker

| Task | Status | Evidence | Next Action |
|---|---|---|---|
| Daily sync baseline | Done | 8b92623 | Monitor sync error handling |
| Minute-level data extension | Done | e482eca, 052454e | Confirm valuation compatibility |
| Sync progress and return reporting | Done | 7f3e394, ada0b55 | Expose metrics consistently to dashboard consumers |
| Sync progress test coverage | Done | 7ec5449 | Sync-progress test breadth broadened |
| Provider and fixture paths | Done | recent feature merges and docs updates | Add explicit operational runbook |

### 5.4 Database and Data Quality

| Task | Status | Evidence | Next Action |
|---|---|---|---|
| Core schema/migrations | Done | `docs/database/schema.sql`, migration folders | Guard migration order in CI |
| Demo data enrichment | Done | 7e6d5be | Expand portfolio scenarios |
| Transaction/position consistency under lock | In Progress | trade/position fixes in repository history | Add focused integration assertions |

### 5.5 Documentation and Integration

| Task | Status | Evidence | Next Action |
|---|---|---|---|
| Architecture baseline | Done | `docs/ARCHITECTURE.md` | Keep diagrams consistent with runtime |
| PRD decomposition | Done | `docs/prd/*` | Track PRD acceptance per release |
| API contract docs | Done | `docs/openapi.yaml`, `docs/API.md` | Add release checklist for contract updates |
| CI quality gates (frontend tests) | Done | 57bb6f2 | Extend CI to cover lint/build and key backend checks |
| Project plan with progress marking | Done | this file | Update weekly or per major merge |

## 6. Current Overall Progress

Estimated overall completion: **96%**

- Completed: core feature set, major integration paths, docs baseline, analytics test suite, sync progress tests, presentation assets
- Remaining: startup reliability hardening, release-level runbook polish, final demo acceptance check

## 7. Risks and Mitigations

| Risk | Severity | Mitigation |
|---|---|---|
| Environment drift between contributors causes startup inconsistency | High | Standardize `.env.example`, validate non-Docker startup in CI job |
| API contract drift from frontend assumptions | Medium | Enforce OpenAPI diff check before merge |
| Data sync/provider volatility affects demos | Medium | Keep fixture fallback and pre-seeded demo snapshots |
| Concurrent trade edge cases regressions | Medium | Add integration tests around locking and idempotency |

## 8. Next 2 Iterations

Iteration A (Stability)
- Normalize non-Docker startup flow for backend and worker
- Add startup troubleshooting notes to docs
- Add focused backend integration tests for datasource + lock path

Iteration B (Release Hardening)
- Add regression checklist for frontend dashboard and trade entry
- Add OpenAPI/runtime consistency gate
- Finalize demo script and acceptance checklist

## 9. Update Protocol

When updating this document:
1. Add new milestone rows with commit hash and date.
2. Update status in each workstream table.
3. Update overall completion percentage.
4. Record blockers and mitigation changes.
5. Keep `Last Updated` current.
6. Record substantive iterative changes (`feat`/`fix`/`ci`) that improve functionality, reliability, observability, or delivery quality, even if they are not large milestone merges.

## 10. Progress Log

- 2026-07-29: Initial project-plan tracker created from repository history and architecture/docs baseline.
- 2026-07-29: Routine sync update completed (`git fetch origin` + delta review). No new commits on `origin/main` relative to local `main`; tracker refreshed and process reaffirmed.
- 2026-07-29: Progress sync rerun completed. `origin/main` still has no delta versus local `main`; remote activity observed on `origin/feature/Viko` (advanced from `d14b250` to `0d4294a`) and flagged for future mainline merge tracking.
- 2026-07-29: Backfilled substantive mainline progress for today: sync progress + return-curve reporting (`ada0b55`/`7f3e394`), environment property-import support (`fcad49f`), and frontend test CI step (`57bb6f2`). Tracking policy updated to include meaningful iterative improvements, not only major merges.
- 2026-07-30: Progress sync completed. Remote delta since last update: PR #23 (Leon) merged `7356bcc`/`62dd9f9`/`903adc1` — AnalyticsService unit tests, expanded controller coverage, sector and helper edge-case tests; PR #24 (feature/Viko) merged `976f78c` — unit-price input prefix style for trade entry UX; PR #25 merged Leon + Viko branches into main (`4b54581`). Dashboard screenshot asset committed (`8c7413a`). Presentation slide order and cover updated. Overall completion raised to 96%.
