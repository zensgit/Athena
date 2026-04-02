# Phase 366: Search Consumer Convergence

## Goal

Start moving Athena frontend search consumers onto the unified `/api/v1/search/query` contract without forcing a large page rewrite.

## Delivered

- Extended backend `/api/v1/search/query` to support `facets` and `suggestions` as include sections.
- Kept `results/context/stats/pivot` support from Phase 365.
- Migrated frontend `nodeService` search consumers to use the unified envelope:
  - `searchNodes(...)`
  - `getAdvancedSearchStats(...)`
  - `getAdvancedSearchPivotStats(...)`

## Design

This slice intentionally converges at the **service layer**, not the page layer.

Why:

- `SearchResults.tsx` and `AdvancedSearchPage.tsx` already depend on stable service return shapes.
- Rewriting those pages first would add UI churn and make validation harder.
- By swapping the transport contract under `nodeService`, the UI keeps the same local state and rendering behavior while the backend contract becomes unified.

## Backend Contract

`POST /api/v1/search/query` now supports these include sections:

- `results`
- `facets`
- `suggestions`
- `context`
- `stats`
- `pivot`

This means the frontend can selectively request only the needed sections per workflow.

## Frontend Consumption Strategy

- `searchNodes(...)`
  - keeps the simple full-text fast path via `GET /search`
  - uses `/search/query` for advanced/faceted paths
  - preserves returned shape: `{ nodes, total, facets, suggestions }`
- `getAdvancedSearchStats(...)`
  - now consumes `stats` from `/search/query`
- `getAdvancedSearchPivotStats(...)`
  - now consumes `pivot` from `/search/query`

## Why This Matters

This removes a major source of contract drift:

- before: frontend mixed `/search/faceted`, `/search/advanced/stats`, `/search/advanced/stats/pivot`
- after: frontend consumers can be served by one normalized search envelope

The remaining migration is mostly page orchestration, not contract fragmentation.

## Follow-up

- Collapse `AdvancedSearchPage` stats + pivot loading into fewer round trips where it improves UX.
- Move URL-state and saved-search replay toward explicit envelope persistence.
- Add richer sort/include/field selection once the first consumer migration is stable.
