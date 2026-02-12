# Phase 1 P98 Design: SearchDialog URL Prefill Fallback

Date: 2026-02-12

## Background

- Advanced Search dialog prefill previously relied on:
  - `ui.searchPrefill` (explicit prefill payload), or
  - `node.lastSearchCriteria` (Redux persisted criteria).
- Under auth redirect/reload flows, both can be empty even when URL already carries valid `/search` state.
- Result: app-bar Search dialog could open without expected criteria prefill.

## Goal

Make Advanced Search dialog prefill deterministic from current route state by adding a shared URL parser and using it as fallback when Redux prefill is absent.

## Scope

- `ecm-frontend/src/utils/searchPrefillUtils.ts` (new shared parser)
- `ecm-frontend/src/components/layout/MainLayout.tsx` (reuse shared parser)
- `ecm-frontend/src/components/search/SearchDialog.tsx` (fallback source)
- `ecm-frontend/src/utils/searchPrefillUtils.test.ts` (unit coverage)

## Implementation

1. Added shared utility `buildSearchPrefillFromAdvancedSearchUrl(pathname, search)`
- Only activates on `/search`.
- Maps URL keys:
  - `q`/`query` -> `name`
  - `previewStatus` -> `previewStatuses` (with alias normalization)
  - `mimeTypes` -> `contentType` (when single value)
  - `creators` -> `createdBy` (when single value)
  - `tags`, `categories`, `minSize`, `maxSize`, `dateRange`

2. Unified MainLayout behavior
- Replaced inline URL parsing logic in app-bar Search action with `searchPrefillUtils`.
- Keeps folder-scoped prefill behavior unchanged.

3. Added dialog-level fallback
- In `SearchDialog`, prefill source order is now:
  1) `searchPrefill`
  2) `lastSearchCriteria`
  3) URL-derived prefill from current `/search` route

## Expected Outcome

- Opening Advanced Search from app-bar on `/search?...` consistently restores current criteria, even when Redux prefill state is empty.
- URL parsing logic no longer drifts between `MainLayout` and `SearchDialog`.
