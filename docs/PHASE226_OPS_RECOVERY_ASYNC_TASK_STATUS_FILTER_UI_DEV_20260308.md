# Phase 226 - Ops Recovery Async Task Status Filter UI - Development

## Date
2026-03-08

## Goal
- 在 Preview Diagnostics 的 Ops Recovery 异步任务中心接入 status 过滤。
- 将 status 过滤同时用于列表查询与终态清理参数传递。

## Implemented

### 1) Service contract extension
- Updated `ecm-frontend/src/services/opsRecoveryService.ts`
  - Added `RecoveryHistoryExportAsyncStatusFilter` type.
  - Extended `listHistoryExportAsyncTasks(...)` to accept optional `status`.
  - Tightened `cleanupHistoryExportAsyncTasks(...)` status type to filter union.

### 2) UI status filter control
- Updated `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
  - Added state:
    - `recoveryHistoryExportAsyncFilterStatus`
  - Added selector:
    - `All / Queued / Running / Completed / Cancelled / Failed`
  - List refresh now sends both:
    - `exportType`
    - `status`

### 3) Cleanup behavior aligned with terminal-only rule
- Updated `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
  - Cleanup now conditionally forwards `status` only when status is terminal:
    - `COMPLETED/CANCELLED/FAILED`
  - For `ALL/QUEUED/RUNNING`, cleanup falls back to default terminal cleanup (avoids 400).

### 4) Mocked E2E extension
- Updated `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`
  - Added list-route status filter simulation.
  - Added UI flow:
    - change status filter to `Completed`
    - refresh list
    - run cleanup
  - Added assertions:
    - list API received `status=COMPLETED`
    - cleanup API received `status=COMPLETED`

## Impact
- 异步任务中心从“按类型过滤”升级为“类型+状态组合过滤”。
- 运维在 UI 层可精确控制清理对象范围，减少误操作风险。
