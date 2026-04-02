# Phase 218 - Ops Recovery Async Export Task Filter by Type - Development

## Date
2026-03-08

## Goal
- 为 Ops Recovery Async Export 任务中心增加“按导出类型筛选”能力。
- 降低任务数量增长后的人工筛查成本，提升任务中心可操作性。

## Implemented

### 1) Backend list filter support
- Updated `ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java`
  - Enhanced endpoint:
    - `GET /api/v1/ops/recovery/history/export-async`
  - Added optional query parameter:
    - `exportType`
  - Added filter normalization helper:
    - `normalizeHistoryExportAsyncTypeFilter`
    - supports `ALL`/blank as no filter
  - Extended task-list builder:
    - `listHistoryExportAsyncTasksInternal(limit, exportTypeFilter)`
    - applies server-side filter by `HistoryExportAsyncType`

### 2) Backend security test extension
- Updated `ecm-core/src/test/java/com/ecm/core/controller/OpsRecoveryControllerSecurityTest.java`
  - Added admin-path assertion:
    - list with `exportType=HISTORY` returns tasks and all returned rows are `HISTORY`.

### 3) Frontend service and UI filter
- Updated `ecm-frontend/src/services/opsRecoveryService.ts`
  - Enhanced:
    - `listHistoryExportAsyncTasks(limit, exportType?)`
  - Sends `exportType` when provided.

- Updated `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
  - Added state:
    - `recoveryHistoryExportAsyncFilterType` (`ALL` or concrete export type)
  - Added UI selector:
    - `Ops recovery async task filter type`
  - Task refresh now calls backend with selected `exportType` filter.

### 4) Mocked E2E update
- Updated `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`
  - Async list route now captures `exportType` query param.
  - Mock list response applies server-side-equivalent filter logic.
  - Added UI interaction:
    - select task filter type = `Summary CSV`
  - Added assertion:
    - list calls include `exportType=HISTORY_SUMMARY`.

## Impact
- Ops Recovery Async Export 任务中心可快速收敛到目标任务类型，减少混合任务列表下的误操作风险。
- 服务端过滤降低前端全量拉取和本地筛选的耦合，为后续任务中心扩展（多维过滤/分页）提供基础。
