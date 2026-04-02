# Phase 196 - Search-Scope Preview Batch Dry-Run API (Development)

## Date
2026-03-07

## Goal
- Add a dry-run API for all-matched failed-preview batch operations.
- Expose dry-run action in Advanced Search before executing retry/rebuild.
- Keep fallback behavior when backend dry-run API is unavailable.

## Implemented

### 1) Backend API
- Updated `ecm-core/src/main/java/com/ecm/core/controller/SearchController.java`:
  - Added admin endpoint:
    - `POST /api/v1/search/preview/queue-failed/dry-run`
  - Reused shared server-side matcher logic with:
    - forced `filters.previewStatuses=['FAILED']`
    - retryable failure filtering
    - optional reason filter
    - capped scan and match limits
  - Added dry-run response DTOs with sampled matched items.

### 2) Backend tests
- Updated:
  - `ecm-core/src/test/java/com/ecm/core/controller/SearchControllerTest.java`
  - `ecm-core/src/test/java/com/ecm/core/controller/SearchControllerSecurityTest.java`
- Added coverage for:
  - dry-run response path
  - admin-only access for dry-run endpoint.

### 3) Frontend integration
- Updated `ecm-frontend/src/services/nodeService.ts`:
  - Added dry-run DTO types and API client:
    - `dryRunFailedPreviewsBySearch(...)`
- Updated `ecm-frontend/src/pages/AdvancedSearchPage.tsx`:
  - Added all-matched dry-run actions:
    - global: `Dry-run all matched (max 200)`
    - per-reason: `Dry-run all`
  - Added dry-run summary panel:
    - matched/sample/scanned/truncated stats
    - sample item names
    - finished timestamp
  - Added client-side fallback dry-run summary generation if backend dry-run API call fails.

### 4) Mock E2E update
- Updated `ecm-frontend/e2e/advanced-search-preview-batch-scope.mock.spec.ts`:
  - Added mock route for `/api/v1/search/preview/queue-failed/dry-run`
  - Added assertions for dry-run payload propagation and no single-document queue side effect.
