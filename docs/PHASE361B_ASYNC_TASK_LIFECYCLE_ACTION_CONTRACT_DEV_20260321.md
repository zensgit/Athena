# Phase 361B: Async Task Lifecycle Action Contract

## Date
- 2026-03-21

## Goal
- Extend Phase 361 from shared `summary` math to shared `list/status/action` semantics.
- Expose one low-risk cross-domain recent-task API for admin governance without breaking any existing task-center endpoints.
- Normalize `cancel/download/cleanup` affordances so the admin control plane can stop hand-coding per-domain action rules.

## Why
- Athena already had a cross-domain async governance overview, but operators still had no shared recent-task feed.
- `search`, `preview`, `ops recovery`, `audit`, and `batch download` each exposed different lifecycle payloads and action semantics.
- `AdminDashboard` can only truly surpass benchmark operator UX if it stops depending on per-domain endpoint quirks.

## Implementation

### 1. Shared lifecycle snapshots
- Added [AsyncTaskActionSnapshot.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskActionSnapshot.java).
- Added [AsyncTaskStatusSnapshot.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskStatusSnapshot.java).
- Added [AsyncTaskLifecycleListSnapshot.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskLifecycleListSnapshot.java).
- These shared snapshots now model:
  - domain identity
  - normalized timestamps
  - filename / actor metadata
  - action URLs
  - `cancellable / cleanupEligible / downloadReady`

### 2. Legacy DTO to shared lifecycle adapters
- Added [AsyncTaskLifecycleAdapters.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskLifecycleAdapters.java).
- The adapter layer now projects:
  - audit async export tasks
  - search dry-run export tasks
  - preview rendition export tasks
  - ops recovery export tasks
  - batch download async tasks
- This keeps legacy task-center DTOs intact while giving the governance plane one internal action contract.

### 3. Shared lifecycle orchestration service
- Added [AsyncTaskLifecycleService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskLifecycleService.java).
- The service is responsible for:
  - bounded paging
  - domain alias normalization
  - filtered total counts
  - cross-domain merge and recency sort
  - adapter projection into shared lifecycle snapshots
- This is still a transitional slice. `search/preview/ops/batch` list retrieval is controller-backed, but the orchestration is no longer embedded in `AnalyticsController`.

### 4. Admin recent-task API
- Added `GET /api/v1/analytics/async-governance/tasks` in [AnalyticsController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/AnalyticsController.java).
- Supported query params:
  - `maxItems`
  - `skipCount`
  - `domain`
  - `status`
- The response now exposes one cross-domain recent-task feed with shared action affordances.

### 5. Batch summary filter support
- Updated [BatchDownloadAsyncTaskRegistry.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/BatchDownloadAsyncTaskRegistry.java) with a filtered `summary(statusFilter)` overload.
- This lets the shared lifecycle service report batch totals consistently when the admin task feed is filtered by status.

## Benchmark Implications
- Against Alfresco, this strengthens Athena’s cross-domain operator surface, which is the clearest current overtake path because Alfresco still keeps downloads, renditions, and search task flows largely separate.
- Against Paperless, this narrows the operator UX gap, but it does not fully close it yet because Athena still lacks a persistent task ledger with acknowledge/dismiss semantics.
- This means Athena can now be stronger in governance breadth, but not yet sharper in day-to-day task inbox polish.

## Files
- [AnalyticsController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/AnalyticsController.java)
- [AsyncTaskActionSnapshot.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskActionSnapshot.java)
- [AsyncTaskStatusSnapshot.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskStatusSnapshot.java)
- [AsyncTaskLifecycleListSnapshot.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskLifecycleListSnapshot.java)
- [AsyncTaskLifecycleAdapters.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskLifecycleAdapters.java)
- [AsyncTaskLifecycleService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskLifecycleService.java)
- [BatchDownloadAsyncTaskRegistry.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/BatchDownloadAsyncTaskRegistry.java)
- [AsyncTaskLifecycleServiceTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/asynctask/AsyncTaskLifecycleServiceTest.java)
- [AnalyticsControllerTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/AnalyticsControllerTest.java)
- [AnalyticsControllerSecurityTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/AnalyticsControllerSecurityTest.java)

## Next
- The next slice should remove controller-backed list orchestration by introducing real shared lifecycle providers for task status and action metadata.
- After that, Athena still needs two benchmark-driven detail slices to surpass on operator polish:
  - persistent task ledger with acknowledge/dismiss semantics
  - structured preflight validation for batch download and similar artifact tasks
