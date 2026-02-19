# Phase 68: Search Error Taxonomy + Recovery Mapping - Development

## Date
2026-02-19

## Background
- Search and Advanced Search both had recovery actions, but retry behavior was not category-aware.
- Query/authorization errors should not always encourage retry, while transient/server errors should.

## Goal
1. Introduce a shared search error classifier and recovery mapping.
2. Reuse mapping in `/search` and `/search-results` for consistent behavior.
3. Keep existing UX actions while making retry gating explicit.

## Changes

### 1) Shared utility
- Added `ecm-frontend/src/utils/searchErrorUtils.ts`
  - `classifySearchError(error)` -> `transient | authorization | query | server | unknown`
  - `resolveSearchErrorMessage(error, fallback)`
  - `buildSearchErrorRecovery(error, fallback)` -> `{ category, message, canRetry, hint }`
- Classifier considers:
  - HTTP status (`400/401/403/422/5xx/408/429`)
  - common text patterns (`network error`, `invalid query`, `forbidden`, etc.)

### 2) Advanced Search integration
- File: `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
  - `searchError` state upgraded to structured recovery object.
  - Catch branch now uses shared utility.
  - Alert shows:
    - normalized error message
    - recovery hint
  - `Retry` action is disabled when `canRetry=false` (e.g., query/auth errors).

### 3) Search Results integration
- File: `ecm-frontend/src/pages/SearchResults.tsx`
  - Added memoized `primarySearchErrorRecovery` from redux `error`.
  - Error alert now shows normalized message + hint.
  - `Retry` button disabled when recovery mapping marks it non-retryable.
  - `Back to folder` / `Advanced` actions remain available.

### 4) Unit tests
- Added `ecm-frontend/src/utils/searchErrorUtils.test.ts`
  - status-based classification
  - text-based classification
  - message resolution
  - retryability mapping

## Non-Functional Notes
- No backend/API contract changes.
- Existing user-visible actions are preserved; only retry eligibility is made smarter.
