# Phase 1 P82 - Advanced Search Fallback Governance Parity (Design) - 2026-02-11

## Background
- `SearchResults` has fallback governance for index eventual consistency, including:
  - bounded auto-retry
  - stale-result dismissal
  - explicit status messaging
- `AdvancedSearchPage` previously lacked equivalent governance, causing inconsistent behavior between search entry points.

## Scope
- Frontend-only changes in search fallback governance logic.
- Primary target: advanced search parity.
- Secondary stabilization: search results retry counter behavior under request transitions.
- Add deterministic E2E coverage for advanced search fallback behavior and keep search-results fallback regression green.

## Changes
1. Add fallback snapshot and criteria binding
- File: `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
- Added:
  - `fallbackResults`
  - `fallbackLabel`
  - `fallbackCriteriaKey`
  - `currentCriteriaKey`
  - `currentCriteriaHasFilters`
  - `dismissedFallbackCriteriaKey`
- Introduced `buildFallbackCriteriaKey(...)` and `hasActiveCriteria(...)` helpers.

2. Add bounded exponential auto-retry
- File: `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
- Added constants:
  - `FALLBACK_AUTO_RETRY_MAX = 3`
  - base delay `1500ms`
  - cap `10000ms`
- Added `getFallbackAutoRetryDelayMs(attempt)`.
- Retry sequence for current settings: `1.5s -> 3.0s -> 6.0s`.

3. Add explicit fallback dismissal action
- File: `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
- Added alert actions:
  - `Retry`
  - `Hide previous results`
- `Hide previous results` suppresses stale cards for current criteria and resets retry counter.

4. Align display semantics with SearchResults
- File: `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
- Added fallback alert content:
  - `Search results may still be indexing...`
  - `Auto-retry X/3 (next in Ys).`
- During fallback, render `displayResults` (fallback snapshot) while keeping current request state.
- Pagination hidden while fallback is active.

5. Add E2E coverage
- File: `ecm-frontend/e2e/advanced-search-fallback-governance.spec.ts`
- Deterministic flow:
  - upload + index one file
  - baseline advanced search hit
  - intercept `/api/v1/search/faceted` for same query with empty results
  - validate fallback alert, backoff message, hide action, and retry stop after hide

6. Stabilize retry counter reset in search-results flow
- File: `ecm-frontend/src/pages/SearchResults.tsx`
- Issue discovered during P82 regression:
  - retry counter was reset while `loading=true` transitions occurred, which could pin message at `Auto-retry 0/3`.
- Fix:
  - only reset counter when not loading and fallback mode is no longer active.

7. Harden E2E retry-step assertions against timing races
- Files:
  - `ecm-frontend/e2e/advanced-search-fallback-governance.spec.ts`
  - `ecm-frontend/e2e/search-fallback-governance.spec.ts`
- Change:
  - replace strict `Auto-retry 1/3 (next in 3.0s)` assertion with progression matcher that accepts:
    - `1/3`, `2/3`, or `stopped after 3 attempts`
  - this prevents false negatives when retry timer advances faster than assertion polling.

## Risk and Mitigation
- Risk: fallback may remain hidden across different searches.
  - Mitigation: dismissal is criteria-key scoped and auto-clears on criteria change.
- Risk: retry timers leaking on re-render.
  - Mitigation: explicit timer cleanup on each effect cycle and unmount.

## Rollback
- Revert `AdvancedSearchPage.tsx` fallback governance additions.
- Remove `e2e/advanced-search-fallback-governance.spec.ts`.
