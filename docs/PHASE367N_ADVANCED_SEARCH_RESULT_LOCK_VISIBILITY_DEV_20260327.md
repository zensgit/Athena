# Phase367N Advanced Search Result Lock Visibility

## Goal

Complete the result-row state story in `AdvancedSearchPage` by surfacing lock state alongside the checkout chip.

## Scope

Frontend-only:

- carry `locked` and `lockedBy` into `SearchResult`
- add a tiny lock-chip helper for advanced-search result rows
- render lock chip in result cards next to checkout/MIME/size/score chips

## Why This Slice

Athena search results already surfaced preview state and, after `Phase367M`, checkout state. Lock state was still invisible in result rows even though the backend already returned it. This slice closes that visibility gap without introducing new actions or backend contract changes.

## Design

- `SearchResult` now includes `locked` and `lockedBy`
- both search-result mapping paths preserve those fields
- `advancedSearchLockUtils` formats the result-row chip:
  - `Locked by bob`
  - fallback `Locked`
- result cards show the chip only when `locked === true`

This mirrors the checkout-chip pattern and keeps the implementation low-conflict.

## Files

- `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
- `ecm-frontend/src/utils/advancedSearchLockUtils.ts`
- `ecm-frontend/src/utils/advancedSearchLockUtils.test.ts`

## Risk

- this phase adds visibility only; it does not add lock actions inside advanced-search result rows
- caller-relative lock semantics remain available in preview/browse surfaces, not in result-row chips
