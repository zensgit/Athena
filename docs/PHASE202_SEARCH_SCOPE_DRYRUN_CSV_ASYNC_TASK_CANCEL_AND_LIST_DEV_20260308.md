# Phase 202 - Search-Scope Dry-Run CSV Async Task Cancel + List - Development

## Date
2026-03-08

## Goal
- 在 Phase 201 的 create/status/download 基础上，补齐导出任务中心关键能力：
  - 任务列表（recent tasks）
  - 任务取消（cancel running/queued task）
- 对齐并超越 Alfresco 的“任务化批处理可运维”方向，降低大导出操作不可控风险。

## Implemented

### 1) Backend: async task list + cancel API
- Updated `ecm-core/src/main/java/com/ecm/core/controller/SearchController.java`:
  - Added:
    - `GET /api/v1/search/preview/queue-failed/dry-run/export-async?limit=...`
    - `POST /api/v1/search/preview/queue-failed/dry-run/export-async/{taskId}/cancel`
  - Status model upgraded:
    - Added `CANCELLED` terminal state.
    - Status payload now includes `createdAt` in addition to `status/error/finishedAt/filename`.
  - Async execution guard:
    - Running worker respects cancellation and will not overwrite cancelled task to completed/failed.
  - Added list response model:
    - `PreviewQueueBySearchDryRunExportAsyncListResponse { count, items[] }`

### 2) Backend tests
- Updated `ecm-core/src/test/java/com/ecm/core/controller/SearchControllerTest.java`:
  - Added recent-task list endpoint test.
  - Added cancel success test (queued/running -> `CANCELLED`, download returns `409`).
  - Added cancel-not-found test.
  - Existing async status assertions updated to include `createdAt`.
- Updated `ecm-core/src/test/java/com/ecm/core/controller/SearchControllerSecurityTest.java`:
  - Added admin/security coverage for list/cancel endpoints.

### 3) Frontend: cancel operation in Advanced Search
- Updated `ecm-frontend/src/services/nodeService.ts`:
  - Added:
    - list async export tasks API
    - cancel async export task API
  - Extended async task types with `createdAt/finishedAt/filename`.
- Updated `ecm-frontend/src/pages/AdvancedSearchPage.tsx`:
  - Export flow now tracks `taskId` while polling.
  - Added `Cancel export` action during exporting.
  - User-triggered cancel transitions to non-error feedback (`cancelled`) instead of generic failure toast.

### 4) Mocked E2E extension
- Updated `ecm-frontend/e2e/advanced-search-preview-batch-scope.mock.spec.ts`:
  - Added second mocked test for cancel flow.
  - Validates:
    - start -> running -> cancel -> cancelled
    - no download request happens after cancellation.
