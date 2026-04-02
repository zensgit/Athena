# Phase 222 - Audit Async Task Governance UI - Development

## Date
2026-03-08

## Goal
- 在 Admin Dashboard 审计任务中心补齐治理 UI：状态摘要与一键清理。
- 对齐后端治理 API，形成端到端操作闭环。

## Implemented

### 1) Summary panel integration
- Updated `ecm-frontend/src/pages/AdminDashboard.tsx`
  - Added summary response type:
    - `AuditExportAsyncSummaryResponse`
  - Added summary state + loader state.
  - Added summary fetch:
    - `GET /analytics/audit/export-async/summary`
  - Added task panel chips:
    - `Total/Active/Terminal/Completed/Failed/Cancelled`

### 2) Cleanup action integration
- Updated `ecm-frontend/src/pages/AdminDashboard.tsx`
  - Added cleanup response type:
    - `AuditExportAsyncCleanupResponse`
  - Added cleanup action:
    - `POST /analytics/audit/export-async/cleanup`
  - Cleanup behavior:
    - if task filter is `COMPLETED/CANCELLED/FAILED`, send matching `status`
    - otherwise cleanup all terminal tasks
  - Added button:
    - `Cleanup tasks`

### 3) Mocked E2E extension
- Updated `ecm-frontend/e2e/admin-audit-filter-export.mock.spec.ts`
  - Added mocked routes:
    - `GET /analytics/audit/export-async/summary`
    - `POST /analytics/audit/export-async/cleanup`
  - Added summary/cleanup assertions:
    - summary route invoked
    - cleanup called with `status=COMPLETED` after filter switch
    - cleanup toast rendered

## Impact
- 运维可以在任务中心直接判断积压结构并执行精准清理。
- 降低手工排障成本，提升长周期运行稳定性和可维护性。
