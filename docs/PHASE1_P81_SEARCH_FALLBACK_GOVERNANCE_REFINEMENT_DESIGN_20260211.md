# Phase 1 P81 - Search Fallback Governance Refinement (Design) - 2026-02-11

## Background
- Search fallback currently shows previous results when the latest search returns empty for the same criteria.
- Existing governance gap:
  - Users cannot explicitly hide stale fallback cards.
  - Auto-retry uses a fixed interval and does not communicate backoff timing.

## Scope
- Frontend search result fallback UX and retry scheduling only.
- No backend API changes.

## Design Changes
1. Add explicit fallback dismissal action
- File: `ecm-frontend/src/pages/SearchResults.tsx`
- In fallback alert action area, add `Hide previous results`.
- Clicking hide:
  - suppresses fallback cards for current criteria key.
  - stops further fallback auto-retry for that criteria.

2. Add exponential backoff for auto-retry
- File: `ecm-frontend/src/pages/SearchResults.tsx`
- Replace fixed retry delay with bounded exponential backoff:
  - base: `1.5s`
  - attempts: `1.5s -> 3.0s -> 6.0s`
  - max delay cap: `10s` (current max attempts is 3, so practical cap not exceeded).

3. Improve fallback message clarity
- File: `ecm-frontend/src/pages/SearchResults.tsx`
- Extend banner text to show next retry delay:
  - `Auto-retry 0/3 (next in 1.5s).`
  - `Auto-retry 1/3 (next in 3.0s).`
  - `Auto-retry 2/3 (next in 6.0s).`

4. Add deterministic E2E coverage
- File: `ecm-frontend/e2e/search-fallback-governance.spec.ts`
- Uses Playwright route interception for same query to force empty responses after one successful baseline result.
- Validates:
  - fallback banner appears
  - hide action hides fallback and returns to empty-state result rendering
  - retry banner reflects backoff progression
  - auto-retry stops once fallback is hidden

## Risk Notes
- Route-level interception is test-only and scoped to one spec.
- Dismissal scope is criteria-key based and auto-clears when criteria changes.

## Rollback
- Revert `SearchResults.tsx` fallback alert/action and retry scheduling changes.
- Remove `e2e/search-fallback-governance.spec.ts`.
