# Phase 237 - Search Preview Batch Capabilities + Worker Tuning (Dev)

Date: 2026-03-09  
Owner: Codex (parallel delivery stream)  
Benchmark track: Alfresco surpass plan (`docs/ALFRESCO_BENCHMARK_SURPASS_PLAN_20260307.md`)

## 1. Objective

Deliver a governance-grade capability contract for search-scope preview retry/rebuild and expose tunable batch worker controls in Advanced Search so operators can:

1. Discover server-side limits instead of relying on hardcoded UI constants.
2. Tune parallelism per operation (`dry-run`, `retry all`, `rebuild all`, `export dry-run CSV`).
3. Observe worker count in execution and dry-run results.

## 2. Backend changes

File:
- `ecm-core/src/main/java/com/ecm/core/controller/SearchController.java`

### 2.1 New capability endpoint

Added admin-only endpoint:

- `GET /api/v1/search/preview/queue-failed/capabilities`

Response:

- `defaultMaxDocuments`
- `maxMaxDocuments`
- `scanPageSize`
- `scanLimit`
- `defaultWorkerCount`
- `maxWorkerCount`

This formalizes runtime governance contract for UI/automation.

### 2.2 Dry-run worker observability

`POST /api/v1/search/preview/queue-failed/dry-run` now returns resolved `workerCount` (clamped by backend bounds).

### 2.3 CSV export metadata alignment

Both sync/async dry-run CSV exports now include:

- `metric,value`
- `"workerCount","<effective-worker-count>"`

This keeps exported artifacts consistent with runtime worker governance.

## 3. Frontend changes

Files:
- `ecm-frontend/src/services/nodeService.ts`
- `ecm-frontend/src/pages/AdvancedSearchPage.tsx`

### 3.1 Service contract

Added:

- `PreviewQueueSearchCapabilities` type
- `getPreviewQueueBySearchCapabilities()` API method
- `workerCount` in dry-run result type

### 3.2 UI worker tuning

Advanced Search preview batch panel now:

1. Loads server capabilities on page init.
2. Provides a `Batch workers` selector (1..maxWorkerCount).
3. Uses selected worker count in:
   - dry-run matched
   - retry/rebuild all matched
   - dry-run async CSV export start
4. Shows scope/worker governance hints:
   - scope cap documents
   - worker range
   - scan limit (when available)

### 3.3 Dynamic max label consistency

All “all matched” action labels now render the dynamic configured cap:

- `Retry all matched (max X)`
- `Rebuild all matched (max X)`
- `Dry-run all matched (max X)`

## 4. Test updates

Backend:
- `SearchControllerTest`
  - added capabilities endpoint assertion
  - added dry-run default/clamped worker assertions
  - CSV export assertion includes `workerCount` metric
- `SearchControllerSecurityTest`
  - added capabilities endpoint admin-only access tests

Frontend e2e:
- `ecm-frontend/e2e/advanced-search-preview-batch-scope.mock.spec.ts`
  - mocked capabilities endpoint
  - selects worker value in UI
  - verifies payload carries selected `workerCount`

## 5. Benchmark mapping (Alfresco comparison)

This phase strengthens operator/runtime governance, matching the direction of Alfresco’s async execution governance (config discovery + runtime status contract), and prepares for next higher-value deltas:

1. failure ledger persistence per `(node, renditionDef)`  
2. queue dedup key governance (`sourceId + rendition`)  
3. content-hash bound rendition validity checks

(Reference scan from parallel analysis task in `reference-projects/alfresco-community-repo`.)

