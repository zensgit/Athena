# Phase359 - Async Governance Summary Adapter Layer (Dev)

## Background
- Phase358 brought batch download into `GET /api/v1/analytics/async-governance/overview`.
- The overview endpoint was still directly coupled to controller-specific nested summary records from:
  - `AnalyticsController`
  - `OpsRecoveryController`
  - `SearchController`
  - `PreviewDiagnosticsController`
  - `BatchDownloadController`
- That coupling made the overview harder to evolve and also hid a semantics gap:
  batch download can report active tasks beyond `queued + running` because
  `cancelRequested` is still an active lifecycle state.

## Goal
Introduce a shared internal async task summary snapshot and adapter layer so
governance aggregation stops unpacking controller-private response shapes
inline.

## Implementation
### 1) Shared snapshot
- Added `ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskSummarySnapshot.java`
- The snapshot normalizes:
  - `totalCount`
  - `activeCount`
  - `terminalCount`
  - `queuedCount`
  - `runningCount`
  - `completedCount`
  - `cancelledCount`
  - `failedCount`
  - `timedOutCount`
  - `expiredCount`
- It also exposes shared derived metrics:
  - `failureCount()`
  - `failureRate()`

### 2) Shared adapter layer
- Added `ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskSummaryAdapters.java`
- Centralized conversion from controller-specific summary responses into the
  shared snapshot for:
  - ops recovery
  - search preview dry-run export
  - preview rendition resources export
  - batch download

### 3) Analytics overview switch
- Updated `ecm-core/src/main/java/com/ecm/core/controller/AnalyticsController.java`
- `getAsyncExportGovernanceOverview()` now consumes the shared snapshot path
  instead of manually unpacking controller-specific response records.
- The local audit summary is also projected into the same snapshot so the
  governance path uses one internal contract end-to-end.

### 4) Batch download active-state fix
- Normalized batch download into the shared lifecycle breakdown instead of
  relying on controller-local aggregate fields.
- `cancelRequested` is treated as active work and folded into the `running`
  bucket for governance math.
- This prevents governance totals from undercounting active batch download
  tasks while keeping the shared snapshot contract simple.

### 5) Test coverage
- Updated `ecm-core/src/test/java/com/ecm/core/controller/AnalyticsControllerTest.java`
- Added a regression that proves the overview counts batch `cancelRequested`
  work as active governance load.

## Why This Slice
- Low conflict: no public API rewrite and no refactor across the large
  controller files.
- Higher leverage than UI parity work because it strengthens an internal
  platform contract used by governance surfaces.
- Creates a clean next step toward moving overview orchestration out of
  `AnalyticsController` entirely.

## Follow-up
1. Move async governance aggregation out of `AnalyticsController` into a shared service or registry.
2. Let domain task centers publish shared summary providers instead of having analytics call controller endpoints.
3. Reuse the snapshot contract for other admin governance panels that still do bespoke summary normalization.
