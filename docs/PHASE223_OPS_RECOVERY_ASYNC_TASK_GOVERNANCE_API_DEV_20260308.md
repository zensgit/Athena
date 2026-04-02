# Phase 223 - Ops Recovery Async Task Governance API - Development

## Date
2026-03-08

## Goal
- 为 Ops Recovery 异步导出任务中心补齐治理能力：任务摘要统计 + 终态清理。
- 降低任务长期堆积后的维护成本，提升可观测性与可运维性。

## Implemented

### 1) Async task summary endpoint
- Updated `ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java`
  - Added endpoint:
    - `GET /api/v1/ops/recovery/history/export-async/summary`
  - Returns:
    - `totalCount`
    - `queuedCount`
    - `runningCount`
    - `completedCount`
    - `cancelledCount`
    - `failedCount`
    - `activeCount`
    - `terminalCount`

### 2) Async task cleanup endpoint
- Updated `ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java`
  - Added endpoint:
    - `POST /api/v1/ops/recovery/history/export-async/cleanup`
  - Supports optional filters:
    - `exportType` (HISTORY/HISTORY_SUMMARY/HISTORY_TREND/HISTORY_COMPARE/HISTORY_COMPARE_BREAKDOWN/HISTORY_COMPARE_ACTORS)
    - `status` (terminal only)
  - Behavior:
    - no `status`: cleanup all terminal tasks (`COMPLETED/CANCELLED/FAILED`)
    - `status` case-insensitive normalization
    - `status=QUEUED/RUNNING` returns `400`
  - Response:
    - `deletedCount`
    - `remainingCount`
    - `exportTypeFilter`
    - `statusFilter`
    - `message`

### 3) Audit trace for governance action
- Updated `ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java`
  - Added audit event:
    - `OPS_RECOVERY_HISTORY_EXPORT_ASYNC_CLEANUP`
  - Records:
    - filters (`exportTypeFilter/statusFilter`)
    - `deletedCount`
    - `remainingCount`

### 4) Security/controller tests
- Updated `ecm-core/src/test/java/com/ecm/core/controller/OpsRecoveryControllerSecurityTest.java`
  - Added `USER` role forbid assertions for:
    - `GET /history/export-async/summary`
    - `POST /history/export-async/cleanup`
  - Added `ADMIN` coverage for:
    - summary successful response
    - cleanup successful response
    - cleanup with `status=RUNNING` returns `400`

## Impact
- Ops Recovery 异步任务具备统一治理面板所需的后端能力。
- 支持按任务类型/状态做受控清理，避免任务中心无限膨胀。
