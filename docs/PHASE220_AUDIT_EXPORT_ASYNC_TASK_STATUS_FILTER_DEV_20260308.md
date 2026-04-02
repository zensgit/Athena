# Phase 220 - Audit Export Async Task Status Filter - Development

## Date
2026-03-08

## Goal
- 为审计异步导出任务中心增加“按状态过滤”能力，提升任务定位效率。
- 在后端执行过滤，避免前端全量拉取后再本地筛选。

## Implemented

### 1) Backend list status filter
- Updated `ecm-core/src/main/java/com/ecm/core/controller/AnalyticsController.java`
  - Enhanced list endpoint:
    - `GET /api/v1/analytics/audit/export-async`
  - Added optional query parameter:
    - `status`
  - Added status parsing method:
    - `parseAuditExportAsyncStatus`
  - Behavior:
    - case-insensitive support: `QUEUED/RUNNING/COMPLETED/CANCELLED/FAILED`
    - invalid value returns `400 Bad Request`
  - Extended list builder to apply server-side status filtering.

### 2) Backend test extension
- Updated `ecm-core/src/test/java/com/ecm/core/controller/AnalyticsControllerTest.java`
  - Added `status=COMPLETED` filter test.
  - Added invalid `status` -> `400` test.

### 3) Frontend filter UI + request passthrough
- Updated `ecm-frontend/src/pages/AdminDashboard.tsx`
  - Added task status filter state:
    - `ALL/QUEUED/RUNNING/COMPLETED/CANCELLED/FAILED`
  - Added `Task status` selector in audit async task panel.
  - `loadAuditExportAsyncTasks` now sends selected `status` query param.
  - Dashboard bootstrap async task fetch also honors current status filter.

### 4) Mocked E2E extension
- Updated `ecm-frontend/e2e/admin-audit-filter-export.mock.spec.ts`
  - Mock list route now captures `status` query param and applies filtered response.
  - Added UI flow:
    - switch `Task status` to `Completed`
    - download completed task
    - switch back to `All statuses`
    - cancel queued task
  - Added assertion:
    - list calls include `status=COMPLETED`.

## Impact
- 在任务量增大时可快速聚焦目标状态任务，显著降低手工查找成本。
- 统一到“服务端过滤 + 前端轻展示”模式，便于后续扩展多维度筛选。
