# Phase 361: Async Task Lifecycle Contract

## Date
- 2026-03-21

## Goal
- Start Phase 361 by converging async task center summary semantics onto a shared lifecycle summary contract.
- Remove provider-level dependence on controller-private summary DTOs for `search`, `preview`, and `ops recovery`.
- Keep all existing REST response shapes unchanged.

## Scope In This Slice
- This slice implements the shared `summary` contract only.
- `list/status/cancel/cleanup/download` convergence is intentionally deferred to later slices so the refactor stays low-risk.

## Problem
- After Phase 360, `AsyncTaskGovernanceConfiguration` still depended on controller summary endpoints and their private response DTOs.
- `SearchController`, `PreviewDiagnosticsController`, and `OpsRecoveryController` each recomputed lifecycle counters in their own local DTO shape.
- The summary math was semantically shared, but still structurally fragmented.

## Implementation

### 1. Shared summary snapshot as the internal contract
- Reused [AsyncTaskSummarySnapshot.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskSummarySnapshot.java) as the internal lifecycle summary contract.
- This snapshot remains the single shared shape for:
  - total
  - active
  - terminal
  - queued
  - running
  - completed
  - cancelled
  - failed
  - timedOut
  - expired

### 2. Search summary convergence
- [SearchController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/SearchController.java) now exposes `summarizeDryRunQueueFailedPreviewsBySearchCsvAsyncTaskSnapshot`.
- The public summary endpoint keeps returning `PreviewQueueBySearchDryRunExportAsyncSummaryResponse`, but now derives it from the shared snapshot through [AsyncTaskSummaryAdapters.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskSummaryAdapters.java).

### 3. Preview rendition summary convergence
- [PreviewDiagnosticsController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java) now exposes `summarizeRenditionResourcesCsvAsyncExportTaskSnapshot`.
- The public `renditions/resources/export-async/summary` endpoint still returns `PreviewRenditionResourcesExportAsyncSummaryResponseDto`, but the DTO is now mapped from the shared snapshot.

### 4. Ops recovery summary convergence
- [OpsRecoveryController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java) now exposes `summarizeHistoryExportAsyncTaskSnapshot`.
- The public `history/export-async/summary` endpoint still returns `RecoveryHistoryExportAsyncSummaryResponseDto`, but now maps from the shared snapshot instead of rebuilding its own response object inline.

### 5. Governance providers now consume the shared contract directly
- [AsyncTaskGovernanceConfiguration.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskGovernanceConfiguration.java) no longer calls the `search/preview/ops` summary endpoints and unwraps their DTOs.
- Providers now consume the new snapshot methods directly, which removes one layer of controller-specific DTO coupling from the governance plane.

### 6. Adapter coverage
- [AsyncTaskSummaryAdapters.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskSummaryAdapters.java) now supports:
  - `snapshot -> search summary DTO`
  - `snapshot -> preview rendition summary DTO`
  - `snapshot -> ops recovery summary DTO`
- Existing `dto -> snapshot` conversion paths remain in place where still needed.

## Result
- Async governance now depends on a shared lifecycle summary contract instead of private summary response DTOs for `search`, `preview`, and `ops`.
- Public REST APIs remain unchanged.
- Summary math is centralized enough that later phases can extend the same pattern to `list/status/action` contracts without redoing the summary path.

## Files
- [AsyncTaskSummaryAdapters.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskSummaryAdapters.java)
- [AsyncTaskGovernanceConfiguration.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskGovernanceConfiguration.java)
- [SearchController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/SearchController.java)
- [PreviewDiagnosticsController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java)
- [OpsRecoveryController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java)
- [AsyncTaskSummaryAdaptersTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/asynctask/AsyncTaskSummaryAdaptersTest.java)

## Next
- Extend the shared lifecycle contract from `summary` to `status/list/action affordances`.
- The next cut should unify action metadata such as `cancel/download/cleanup` eligibility and URLs, while keeping legacy endpoint payloads as adapters for one compatibility cycle.
