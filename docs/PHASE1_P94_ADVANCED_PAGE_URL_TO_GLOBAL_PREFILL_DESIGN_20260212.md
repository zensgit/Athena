# Phase 1 P94 Design: Map Advanced-Search URL State into Global Search Dialog

## Background
- `AdvancedSearchPage` (`/search`) keeps its criteria in URL query params (`q`, `previewStatus`, `mimeTypes`, etc.).
- Global app-bar search button opens `SearchDialog`.
- Before this change, opening global dialog from `/advanced-search` could lose current URL criteria context.

## Goal
- When user clicks app-bar search on `/search`, prefill global `SearchDialog` from current URL state.

## Scope
- Frontend only.
- Entry: `MainLayout` app-bar search handler.
- Regression: Playwright E2E.

## Implementation
1. Extend app-bar search prefill assembly
- File: `ecm-frontend/src/components/layout/MainLayout.tsx`
- Added `useLocation` and URL parsing helpers.
- On `/search`, parse:
  - `q` -> `name`
  - `previewStatus` -> `previewStatuses`
  - `mimeTypes` (single value only) -> `contentType`
  - `creators` (single value only) -> `createdBy`
  - `tags`, `categories`
  - `minSize`, `maxSize`
  - `dateRange` -> `modifiedFrom` (relative conversion)

2. Preserve folder scope behavior
- Existing folder-scope prefill logic remains and merges with advanced URL prefill.

3. Dispatch strategy
- Only dispatch `setSearchPrefill` when at least one prefill field is present.
- Always dispatch `setSearchOpen(true)` to open dialog.

4. E2E regression
- File: `ecm-frontend/e2e/search-dialog-active-criteria-summary.spec.ts`
- Added scenario:
  - open `/search` with URL state,
  - click app-bar search,
  - assert dialog fields/summary reflect URL criteria.

## Expected Outcome
- Global search dialog behaves as a continuation of Advanced Search page state.
- Users no longer need to re-enter active criteria when switching search surfaces.
