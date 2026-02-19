# Phase 65: Search Recoverable Error Actions - Development

## Date
2026-02-18

## Background
- On search failures, recovery actions were inconsistent:
  - Search Results error alert only exposed `Advanced`
  - Advanced Search relied on toast only, with no inline retry controls
- This slowed operator recovery after transient backend/index errors.

## Goal
1. Provide explicit, in-context recovery actions for search failures.
2. Keep actions consistent with folder-centric workflow.
3. Avoid changing backend APIs.

## Changes

### 1) Search Results (`/search-results`)
- File: `ecm-frontend/src/pages/SearchResults.tsx`
- Enhanced top-level error alert actions:
  - `Retry` (re-executes last search criteria; falls back to current quick query)
  - `Back to folder` (navigate to `/browse/root`)
  - `Advanced` (existing action kept)
- Added helper callbacks:
  - `handleRetryPrimarySearch`
  - `handleGoHomeFromError`

### 2) Advanced Search (`/search`)
- File: `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
- Added local `searchError` state:
  - reset on each new search attempt
  - populated from backend error message or fallback `"Search failed"`
- Added inline recoverable error alert in results area:
  - `Retry` (re-run current search page)
  - `Back to folder` (navigate to `/browse/root`)
- Existing toast remains for immediate feedback.

## Behavior Notes
- No API or payload changes.
- Recovery controls appear only when a search failure is present.
- Sidebar auto-collapse preference is respected when navigating back to folder.
