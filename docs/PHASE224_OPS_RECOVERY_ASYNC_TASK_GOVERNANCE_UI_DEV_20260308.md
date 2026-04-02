# Phase 224 - Ops Recovery Async Task Governance UI - Development

## Date
2026-03-08

## Goal
- 在 Preview Diagnostics 的 Ops Recovery 区域接入异步导出任务治理能力。
- 提供 summary 可视化与一键 cleanup 操作，缩短运维闭环。

## Implemented

### 1) Ops recovery async summary integration
- Updated `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
  - Added summary state:
    - `recoveryHistoryExportAsyncSummary`
    - `recoveryHistoryExportAsyncSummaryLoading`
  - Added summary refresh logic:
    - fetches `/ops/recovery/history/export-async/summary`
  - Added UI chips:
    - `Async total`
    - `Active`
    - `Completed`
    - `Cancelled`
    - `Failed`
  - Added action:
    - `Refresh summary`

### 2) Ops recovery async cleanup integration
- Updated `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
  - Added cleanup state:
    - `recoveryHistoryExportAsyncCleaning`
  - Added cleanup action:
    - `Cleanup ops recovery async export tasks`
    - Calls `/ops/recovery/history/export-async/cleanup`
    - Carries current type filter (`recoveryHistoryExportAsyncFilterType`) as optional `exportType`
  - Added success/info/error toast feedback.

### 3) Mocked E2E route + assertion extension
- Updated `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`
  - Added mocked routes:
    - `GET /api/v1/ops/recovery/history/export-async/summary`
    - `POST /api/v1/ops/recovery/history/export-async/cleanup`
  - Added test flow:
    - trigger cleanup button
    - verify cleanup toast
  - Added assertions:
    - summary API called
    - cleanup API called with current export-type context

## Impact
- Ops Recovery 异步导出任务从“可查看”提升为“可治理”。
- 前后端治理能力对齐，支持长期运行场景下任务中心维护。
