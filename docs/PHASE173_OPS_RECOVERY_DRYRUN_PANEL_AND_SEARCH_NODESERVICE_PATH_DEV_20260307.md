# Phase 173 - Ops Recovery Dry-run Panel + Search NodeService Path (Development)

## Date
2026-03-07

## Goal
- Make recovery operations safer for admins by adding a first-class dry-run control plane in Preview Diagnostics.
- Remove direct advanced-search page dependency on raw `/search/faceted` payload shape by reusing `nodeService.searchNodes`.

## Implemented

### 1) Preview diagnostics: Ops Recovery Dry-run panel
- Updated `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`:
  - Added a dedicated `Ops Recovery Dry-run` section.
  - Added operator controls:
    - mode (`QUEUE_BY_REASON`, `REPLAY_BATCH`, `UNBLOCK_REQUEUE_BATCH`);
    - reason;
    - category;
    - retryable toggle;
    - max documents;
    - force flag.
  - Added `Run Dry-run` action wired to `opsRecoveryService.dryRun`.
  - Added dry-run summary chips (`matched`, `estimatedQueued`, `estimatedSkipped`, `estimatedFailed`, etc.).
  - Added dry-run sample table for previewing affected documents/outcomes.

### 2) Advanced search: unify request path through NodeService
- Updated `ecm-frontend/src/pages/AdvancedSearchPage.tsx`:
  - Replaced direct `apiService.post('/search/faceted')` call with `nodeService.searchNodes(...)`.
  - Mapped `Node[]` from service response to existing `SearchResult[]` view model.
  - Replaced page total computation with `response.total`.
  - Added `normalizeSearchFacets` adapter to map generic service facets into page-local facet schema without unsafe type assertion.

### 3) E2E mock compatibility
- Existing mock route `POST /api/v1/ops/recovery/dry-run` in:
  - `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`
- Verified to work with new dry-run panel wiring and toast/result rendering path.

## Design notes
- Recovery dry-run panel is intentionally colocated with diagnostics and policy controls so operators can complete:
  1) inspect failures,
  2) estimate impact,
  3) apply scoped/batch recovery,
  in a single page without hidden API-only flows.
- `AdvancedSearchPage` now consumes one service contract (`nodeService.searchNodes`) used elsewhere, reducing endpoint-specific drift and improving fallback/ACL compatibility.
