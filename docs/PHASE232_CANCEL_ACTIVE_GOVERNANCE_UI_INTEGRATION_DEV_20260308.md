# Phase 232 - Cancel-Active Governance UI Integration - Development

## Date
2026-03-08

## Goal
- 将 Phase 229 的 `cancel-active` 后端能力接入 Admin/Audit 与 PreviewDiagnostics/Ops 页面。
- 在不破坏既有单任务取消/清理流程的前提下，补齐批量治理入口与 mocked E2E 覆盖。

## Implemented

### 1) Admin Dashboard (Audit) UI integration
- Updated `ecm-frontend/src/pages/AdminDashboard.tsx`
  - Added `AuditExportAsyncCancelActiveResponse` contract.
  - Added action state `auditExportAsyncCancellingActive`.
  - Added handler:
    - `handleCancelActiveAuditExportAsyncTasks()`
    - calls `POST /api/v1/analytics/audit/export-async/cancel-active`
    - forwards `status` only when current filter is active (`QUEUED/RUNNING`).
  - Added button in task actions:
    - `Cancel active tasks`
    - `aria-label="Cancel active audit async export tasks"`

### 2) Preview Diagnostics (Ops) UI integration
- Updated `ecm-frontend/src/services/opsRecoveryService.ts`
  - Added types:
    - `RecoveryHistoryExportAsyncActiveStatusFilter`
    - `RecoveryHistoryExportAsyncTaskCancelActiveResponse`
  - Added API:
    - `cancelActiveHistoryExportAsyncTasks(exportType?, status?)`
    - calls `POST /api/v1/ops/recovery/history/export-async/cancel-active`
- Updated `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
  - Added state `recoveryHistoryExportAsyncCancellingActive`.
  - Added handler:
    - `handleCancelActiveRecoveryHistoryExportAsyncTasks()`
    - aligns filters with current task center (`exportType`, active-only `status`).
  - Added button:
    - `Cancel active tasks`
    - `aria-label="Cancel active ops recovery async export tasks"`

### 3) Mocked E2E integration
- Updated `ecm-frontend/e2e/admin-audit-filter-export.mock.spec.ts`
  - Added mock route:
    - `POST /api/v1/analytics/audit/export-async/cancel-active`
  - Added call tracking and assertions for `status` filter usage.
  - Added UI flow coverage for clicking `Cancel active audit async export tasks`.
- Updated `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`
  - Added mock route:
    - `POST /api/v1/ops/recovery/history/export-async/cancel-active`
  - Added UI action flow for `Cancel active ops recovery async export tasks`.
  - Preserved existing summary/cleanup assertions and cross-panel regression checks.

## Impact
- Audit/Ops 两个任务中心都可直接进行批量“取消活动任务”，与后端治理契约对齐。
- 运维操作从“按 taskId 手工逐条取消”升级为“按过滤条件批量治理”，效率与一致性提升。

