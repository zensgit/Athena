# Phase 228 - Async Export Filter-Aligned Summary UI - Development

## Date
2026-03-08

## Goal
- 将 Admin/Audit 与 PreviewDiagnostics/Ops 的 async export summary 与当前过滤器联动。
- 消除任务中心“过滤列表 vs 全量 summary”口径差异。

## Implemented

### 1) Admin Dashboard (Audit)
- Updated `ecm-frontend/src/pages/AdminDashboard.tsx`
  - `loadAuditExportAsyncSummary(...)` 增加 status 入参并透传 query。
  - `loadAuditExportAsyncTasks(...)` 刷新后调用 summary 时传入同一 `statusFilter`。
  - `fetchDashboard(...)` 初始化加载时，summary 请求根据当前 task status 过滤器带上 `status` 参数。

### 2) Preview Diagnostics (Ops Recovery)
- Updated `ecm-frontend/src/services/opsRecoveryService.ts`
  - Added `RecoveryHistoryExportAsyncStatusFilter`.
  - Extended:
    - `listHistoryExportAsyncTasks(limit, exportType, status)`
    - `getHistoryExportAsyncTaskSummaryFiltered(exportType, status)`
- Updated `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
  - summary 请求使用当前 `exportType + status` 过滤值。
  - async task 列表增加 status 过滤器下拉（All/Queued/Running/Completed/Cancelled/Failed）。
  - cleanup 仅在 terminal status 选中时透传 `status`，其余走默认 terminal cleanup。

### 3) Mocked E2E alignment
- Updated:
  - `ecm-frontend/e2e/admin-audit-filter-export.mock.spec.ts`
  - `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`
- Added/updated assertions:
  - summary 接口收到过滤参数（`status`，以及 ops 的 `exportType`）
  - cleanup 与列表在过滤上下文一致
  - 关键交互仍保持通过（download/cancel/cleanup）

## Impact
- 任务中心统计与列表一致，降低误判风险。
- 过滤治理闭环更清晰，为批量运维决策提供更可靠读数。
