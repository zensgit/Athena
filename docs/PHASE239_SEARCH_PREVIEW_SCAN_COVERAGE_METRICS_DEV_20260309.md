# Phase 239 - Search Preview Scan Coverage Metrics (Dev)

Date: 2026-03-09  
Owner: Codex (parallel delivery stream)  
Benchmark track: Alfresco surpass plan (`docs/ALFRESCO_BENCHMARK_SURPASS_PLAN_20260307.md`)

## 1. Objective

Add scan-coverage observability for search-scope preview retry workflows by exposing how many scanned candidates were skipped.

This makes dry-run/queue outputs easier to interpret in large result sets and improves operator actionability.

## 2. Backend changes

File:
- `ecm-core/src/main/java/com/ecm/core/controller/SearchController.java`

### 2.1 New response metric

Added `scanSkipped` to:

1. `PreviewQueueBySearchResponse`
2. `PreviewQueueBySearchDryRunResponse`

`scanSkipped` is computed as the sum of `skipBreakdown[*].count`.

### 2.2 CSV metric alignment

Dry-run CSV now includes:

- `metric,value`
- `"scanSkipped","<count>"`

This keeps exported diagnostics aligned with API/UI responses.

### 2.3 Test coverage updates

File:
- `ecm-core/src/test/java/com/ecm/core/controller/SearchControllerTest.java`

Updated assertions:

1. Queue response includes `scanSkipped`.
2. Dry-run response includes `scanSkipped`.
3. Skip-diagnostics case validates expected skipped total.
4. Sync/async dry-run CSV includes `scanSkipped`.

## 3. Frontend changes

Files:
- `ecm-frontend/src/services/nodeService.ts`
- `ecm-frontend/src/pages/AdvancedSearchPage.tsx`

### 3.1 API contract

Added optional `scanSkipped` to:

1. `PreviewQueueSearchBatchResult`
2. `PreviewQueueSearchDryRunResult`

### 3.2 UI summary

Advanced Search preview batch panel now shows skipped scan coverage:

1. Batch execution label includes `scanned X, skipped Y, workers Z`.
2. Dry-run summary line includes `skipped Y`.
3. Dry-run toast summary includes skipped count.

## 4. Mock e2e updates

File:
- `ecm-frontend/e2e/advanced-search-preview-batch-scope.mock.spec.ts`

Updated mocked queue/dry-run payloads to include `scanSkipped`, and added UI assertion to validate skipped coverage appears in dry-run summary text.

## 5. Benchmark mapping

This phase moves search-scope retry governance toward stronger operator explainability by coupling:

1. skip reason distribution (`skipBreakdown`)
2. skipped total (`scanSkipped`)
3. execution parallelism (`workerCount`)

in a single observable contract across API, UI, and CSV.
