# Phase 1 (P76) - Search Fallback Criteria Guard Design (2026-02-11)

## Background
- In search results, the fallback banner (`Search results may still be indexing`) is intended only for the same query criteria while index freshness catches up.
- We observed flaky behavior in E2E and UI sessions where stale cards could appear after query transitions.

## Problem Statement
- Fallback data (`fallbackNodes`, `fallbackLabel`, `fallbackCriteriaKey`) must remain bound to the criteria that produced those nodes.
- If this snapshot is updated at the wrong moment, a later query can reuse stale data and show misleading cards.
- Existing E2E miss-query text (`e2e-fallback-miss-...`) was not collision-resistant enough and could still match indexed tokens.

## Scope
- Frontend search page fallback synchronization only.
- One focused E2E scenario for criteria-change fallback guard.
- No backend API or schema changes.

## Design Decisions
1. Update fallback snapshot only when `nodes` changes.
2. Read criteria through a ref (`lastSearchCriteriaRef`) at snapshot time.
3. Keep existing fallback criteria-key comparison logic and auto-retry behavior.
4. Strengthen miss-query generation in E2E with low-collision random alpha suffix.

## Implementation Details
- File: `ecm-frontend/src/pages/SearchResults.tsx`
  - Added `lastSearchCriteriaRef` and synchronized it in a dedicated effect.
  - Changed fallback snapshot effect dependency to `[nodes]` only.
  - Snapshot now reads:
    - `setFallbackNodes(nodes)`
    - `setFallbackLabel((criteria?.name || '').trim())`
    - `setFallbackCriteriaKey(buildFallbackCriteriaKey(criteria))`
- File: `ecm-frontend/e2e/search-fallback-criteria.spec.ts`
  - Added `randomAlpha(length)` helper.
  - Changed miss-query to `nomatch${Date.now()}${randomAlpha(18)}`.
  - Kept expected assertions:
    - show `0 results found`
    - no fallback banner
    - no stale hit-card from previous query.

## Compatibility
- No user-facing API changes.
- No Redux schema changes.
- No migration required.

## Risks and Mitigations
- Risk: stricter fallback snapshot timing could hide fallback in edge timing windows.
- Mitigation: criteria-key guard and auto-retry remain unchanged; targeted E2E validates query-change behavior.

