# Phase 217 - Ops Recovery History Async Export Task Center - Development

## Date
2026-03-08

## Goal
- 将 Ops Recovery History 导出能力从同步下载升级为异步任务中心，支持启动、轮询、取消、下载。
- 对齐 Athena 现有 async export 交互模型，降低运维学习和排障成本。

## Implemented

### 1) Backend async export task APIs
- Added `ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java`
  - Added async task storage:
    - `historyExportAsyncTasks` (`ConcurrentHashMap`)
    - `historyExportAsyncTaskOrder` (`Deque`)
  - Added endpoints:
    - `POST /api/v1/ops/recovery/history/export-async`
    - `GET /api/v1/ops/recovery/history/export-async`
    - `GET /api/v1/ops/recovery/history/export-async/{taskId}`
    - `POST /api/v1/ops/recovery/history/export-async/{taskId}/cancel`
    - `GET /api/v1/ops/recovery/history/export-async/{taskId}/download`
  - Added async task lifecycle helpers:
    - request snapshot copy / task create / task run / task list / task trim
  - Added task status model:
    - `QUEUED`, `RUNNING`, `COMPLETED`, `CANCELLED`, `FAILED`
  - Async execution supports export types:
    - `HISTORY`
    - `HISTORY_SUMMARY`
    - `HISTORY_TREND`
    - `HISTORY_COMPARE`
    - `HISTORY_COMPARE_BREAKDOWN`
    - `HISTORY_COMPARE_ACTORS`

### 2) Backend security coverage
- Added `ecm-core/src/test/java/com/ecm/core/controller/OpsRecoveryControllerSecurityTest.java`
  - Added `USER` forbidden checks for all async endpoints.
  - Added `ADMIN` flow checks:
    - start/list async tasks
    - non-existing task status/cancel/download returns `404`.

### 3) Frontend task center integration
- Added `ecm-frontend/src/services/opsRecoveryService.ts`
  - Added async export DTOs:
    - `RecoveryHistoryExportAsyncRequest`
    - `RecoveryHistoryExportAsyncCreateResponse`
    - `RecoveryHistoryExportAsyncTaskStatus`
    - `RecoveryHistoryExportAsyncTaskList`
  - Added async task APIs:
    - `startHistoryExportAsync`
    - `listHistoryExportAsyncTasks`
    - `getHistoryExportAsyncTask`
    - `cancelHistoryExportAsyncTask`
    - `downloadHistoryExportAsyncTask`

- Updated `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
  - Added ops recovery async export state and actions.
  - Added UI controls:
    - async export type selector
    - `Start Async Export`
    - `Refresh async tasks`
  - Added async task table with row-level `Cancel` and `Download`.

### 4) Mocked E2E integration
- Updated `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`
  - Added mocked routes for ops recovery async export task center endpoints.
  - Added assertions for start/list/cancel/download flow.
  - Stabilized task-row assertion strategy for strict locator mode.

## Impact
- Ops Recovery 大窗口导出不再阻塞在同步请求，可在任务中心观察和控制执行状态。
- 与 Search/Preview 侧 async export 模型对齐，便于后续统一任务平台能力（治理、清理、审计、配额）。
