# Phase 230 - Preview Rendition Async Task Governance API/UI - Development

## Date
2026-03-08

## Goal
- 为 Preview Diagnostics 的 rendition resources async export 补齐完整治理能力：
  - 列表状态过滤
  - summary 汇总
  - cleanup 清理
- 前后端口径一致，支持按筛选维度闭环运维。

## Implemented

### 1) Backend governance API
- Updated `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`
  - Enhanced list endpoint:
    - `GET /api/v1/preview/diagnostics/renditions/resources/export-async`
    - added optional `status`
  - Added summary endpoint:
    - `GET /api/v1/preview/diagnostics/renditions/resources/export-async/summary`
    - returns `total/queued/running/completed/cancelled/failed/active/terminal`
  - Added cleanup endpoint:
    - `POST /api/v1/preview/diagnostics/renditions/resources/export-async/cleanup`
    - default cleanup: all terminal tasks
    - optional `status`: only `COMPLETED/CANCELLED/FAILED`
    - non-terminal status filter returns `400`
  - Added status parser with unified invalid-status error:
    - `Unknown async export status: <value>`

### 2) Backend security/controller tests
- Updated `ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java`
  - USER:
    - new `summary/cleanup` endpoints remain `403`
  - ADMIN:
    - list supports `status` filter
    - summary returns expected counters and count relationships
    - cleanup default and status-filter semantics validated
    - non-terminal cleanup status returns `400`

### 3) Frontend service + UI governance
- Updated `ecm-frontend/src/services/previewDiagnosticsService.ts`
  - Added rendition async export APIs:
    - list by optional `status`
    - summary by optional `status`
    - cleanup by optional `status`
- Updated `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
  - Added async task status filter:
    - `ALL/QUEUED/RUNNING/COMPLETED/CANCELLED/FAILED`
  - Added summary chips:
    - total/active/queued/running/completed/cancelled/failed/terminal
  - Cleanup action now aligned to current status filter.
  - Refresh keeps list/summary filter context consistent.

### 4) Mocked E2E updates
- Updated `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`
  - Added route mocks and call tracking for:
    - list with `status`
    - summary with `status`
    - cleanup with `status`
  - Added assertions for filtered calls and cleanup effects.
  - Hardened ambiguous chip text assertions with deterministic `.first()` selection to avoid strict-mode collisions in shared dashboard texts.

## Impact
- Preview rendition async export 任务中心具备与 Audit/Ops 对齐的治理能力。
- 运维可以基于同一过滤条件查看、清理和核对统计，降低误操作与误判风险。

