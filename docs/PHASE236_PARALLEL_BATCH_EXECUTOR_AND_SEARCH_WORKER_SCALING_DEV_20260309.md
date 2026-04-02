# Phase 236 - Parallel Batch Executor and Search Worker Scaling - Development

## Date
2026-03-09

## Goal
- 对标 Alfresco `BatchProcessor` 并补强 Athena 批处理能力：在保持结果可追踪的前提下支持并发 worker 执行。
- 将并发批处理能力接入 `search/preview/queue-failed`，提升 all-matched 批量重试吞吐。

## Implemented

### 1) Generic batch executor parallel mode
- Updated `ecm-core/src/main/java/com/ecm/core/batch/BatchExecutor.java`
  - Added `runParallel(...)`:
    - bounded worker pool (`min(workerCount, items.size())`, floor `1`)
    - preserves input order in output payload
    - retains existing aggregated counters (`succeeded/skipped/failed`)
    - exception mapping kept consistent with sequential `run(...)`
  - Added shared item execution helper for consistent outcome accounting.

### 2) Search preview batch endpoint worker scaling
- Updated `ecm-core/src/main/java/com/ecm/core/controller/SearchController.java`
  - `POST /api/v1/search/preview/queue-failed` now executes with `BatchExecutor.runParallel(...)`.
  - Added worker governance:
    - default worker count: `4`
    - clamp range: `1..16`
  - Extended request DTO:
    - `PreviewQueueBySearchRequest.workerCount` (optional)
  - Extended response DTO:
    - `PreviewQueueBySearchResponse.workerCount` (effective worker count)
  - Snapshot copy method now carries `workerCount`.

### 3) Frontend contract alignment
- Updated `ecm-frontend/src/services/nodeService.ts`
  - Added optional `workerCount` to search preview queue/dry-run/export request payload types.
  - Added optional `workerCount` in `PreviewQueueSearchBatchResult`.
- Updated `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
  - Introduced `PREVIEW_BATCH_WORKER_COUNT = 4`.
  - Sends `workerCount` in search-scope queue/dry-run/export payloads.
  - Batch result label now echoes effective worker count for operator observability.

### 4) Test coverage extension
- Updated `ecm-core/src/test/java/com/ecm/core/batch/BatchExecutorTest.java`
  - Added parallel ordering/outcome aggregation test.
  - Added single-worker fallback equivalence test.
  - Added parallel exception mapping test.
- Updated `ecm-core/src/test/java/com/ecm/core/controller/SearchControllerTest.java`
  - Existing queue test now asserts default `workerCount=4`.
  - Added clamp test asserting oversized input is bounded to `16`.

## Impact
- Athena search-scope preview recovery now has a controllable parallel execution path with deterministic result ordering.
- This closes a key benchmark gap in “通用批处理框架（并发 worker + 统计）” while keeping existing API behavior backward-compatible.
