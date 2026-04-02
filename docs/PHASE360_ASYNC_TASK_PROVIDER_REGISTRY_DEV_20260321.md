# Phase 360: Async Task Provider Registry

## Date
- 2026-03-21

## Goal
- Move async governance orchestration out of `AnalyticsController` and into a shared provider/registry/service slice.
- Extract audit async export task state from controller-local maps into a reusable registry.
- Keep `/api/v1/analytics/async-governance/overview` and the existing audit async export APIs backward-compatible.

## Why
- `AnalyticsController` had become both an API layer and a task-state/orchestration layer.
- Async governance overview depended on `controller -> controller` calls and private summary wiring.
- Audit async export was still a controller-local special case, unlike newer task centers such as batch download.

## Implementation

### 1. Audit async export registry
- Added [AuditExportAsyncTaskRegistry.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/AuditExportAsyncTaskRegistry.java).
- The registry now owns:
  - task creation
  - running/completed/failed/cancelled transitions
  - list and summary snapshots
  - cancel-active and cleanup-terminal operations
  - bounded terminal-task retention
- `AnalyticsController` now delegates audit async export state handling to this registry instead of keeping in-memory maps and locks.

### 2. Async governance provider model
- Added shared async governance types under [ecm-core/src/main/java/com/ecm/core/asynctask](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/asynctask):
  - `AsyncTaskGovernanceProvider`
  - `SimpleAsyncTaskGovernanceProvider`
  - `AsyncTaskGovernanceProviderRegistry`
  - `AsyncTaskGovernanceService`
  - `AsyncTaskGovernanceOverviewSnapshot`
  - `AsyncTaskGovernanceDomainSnapshot`
  - `AsyncTaskGovernanceStatus`
  - `AsyncTaskGovernanceRiskLevel`
- `AsyncTaskGovernanceConfiguration` now registers five providers in a stable order:
  - `audit`
  - `ops`
  - `search`
  - `preview`
  - `batchDownload`

### 3. Controller slimming
- Updated [AnalyticsController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/AnalyticsController.java) to inject:
  - `AuditExportAsyncTaskRegistry`
  - `AsyncTaskGovernanceService`
- Removed controller-local audit async task storage and the controller-side async governance aggregation logic.
- The controller now does two thin responsibilities only:
  - call registry/service
  - map internal snapshots into the existing response DTO shape

### 4. Shared summary normalization
- `AsyncTaskSummaryAdapters` remains the normalization boundary for task-center-specific summary payloads.
- Batch download `cancelRequested` work is still normalized into the shared `running` bucket so governance totals stay coherent.

## Compatibility
- No public endpoint shape was changed in this phase.
- `/api/v1/analytics/async-governance/overview` keeps the same response fields.
- Existing audit async export endpoints keep the same semantics for:
  - create
  - list
  - status
  - summary
  - cancel
  - cancel-active
  - download
  - cleanup

## Files
- [AnalyticsController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/AnalyticsController.java)
- [AuditExportAsyncTaskRegistry.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/AuditExportAsyncTaskRegistry.java)
- [AsyncTaskGovernanceConfiguration.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskGovernanceConfiguration.java)
- [AsyncTaskGovernanceService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskGovernanceService.java)
- [AsyncTaskGovernanceProviderRegistry.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskGovernanceProviderRegistry.java)
- [AsyncTaskGovernanceProvider.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskGovernanceProvider.java)
- [AsyncTaskGovernanceOverviewSnapshot.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskGovernanceOverviewSnapshot.java)
- [AsyncTaskGovernanceDomainSnapshot.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskGovernanceDomainSnapshot.java)
- [AsyncTaskGovernanceStatus.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskGovernanceStatus.java)
- [AsyncTaskGovernanceRiskLevel.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskGovernanceRiskLevel.java)
- [SimpleAsyncTaskGovernanceProvider.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/asynctask/SimpleAsyncTaskGovernanceProvider.java)
- [AsyncTaskSummaryAdapters.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/asynctask/AsyncTaskSummaryAdapters.java)
- [AnalyticsControllerTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/AnalyticsControllerTest.java)
- [AnalyticsControllerSecurityTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/AnalyticsControllerSecurityTest.java)
- [AsyncTaskGovernanceServiceTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/asynctask/AsyncTaskGovernanceServiceTest.java)

## Next
- Phase 361 should extract a shared async lifecycle contract for `list/status/summary/cancel/cleanup/download`, so the provider registry stops depending on controller summary response types for `ops/search/preview`.
