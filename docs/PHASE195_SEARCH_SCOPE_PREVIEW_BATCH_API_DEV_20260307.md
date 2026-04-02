# Phase 195 - Search-Scope Preview Batch Queue API (Development)

## Date
2026-03-07

## Goal
- Introduce backend-native all-matched failed preview queue API.
- Reduce frontend multi-page scan/queue round trips.
- Keep existing frontend client-side scan as fallback.

## Implemented

### 1) Backend API
- Updated `ecm-core/src/main/java/com/ecm/core/controller/SearchController.java`:
  - Added admin endpoint:
    - `POST /api/v1/search/preview/queue-failed`
  - Request supports:
    - `query`, `filters`, `sortBy`, `sortDirection`, `reason`, `maxDocuments`, `force`
  - Server behavior:
    - Forces `filters.previewStatuses=['FAILED']`
    - Iterates advanced-search pages server-side
    - Matches retryable failures only (`TEMPORARY`)
    - Optional reason filter
    - Bounded by:
      - `maxDocuments` (clamped)
      - scan ceiling (`MAX_PREVIEW_QUEUE_SCAN_LIMIT`)
  - Returns batch summary + per-item outcomes.

### 2) Backend tests
- Updated:
  - `ecm-core/src/test/java/com/ecm/core/controller/SearchControllerTest.java`
  - `ecm-core/src/test/java/com/ecm/core/controller/SearchControllerSecurityTest.java`
- Added coverage for:
  - endpoint response path
  - admin-only access enforcement.

### 3) Frontend integration
- Updated `ecm-frontend/src/services/nodeService.ts`:
  - Added `queueFailedPreviewsBySearch(...)` API client and DTO types.
- Updated `ecm-frontend/src/pages/AdvancedSearchPage.tsx`:
  - All-matched actions now call backend batch API first.
  - Kept client-side multi-page scan as fallback path.
  - Progress/toast now supports backend batch summary payload.

### 4) Mock E2E update
- Updated `ecm-frontend/e2e/advanced-search-preview-batch-scope.mock.spec.ts`:
  - Added mock route for `/api/v1/search/preview/queue-failed`
  - Asserted request payload propagation (`query`, `maxDocuments`, `filters.previewStatuses`, `reason`)
  - Kept current-page retry queue assertion to avoid regression.
