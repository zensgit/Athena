# Phase 231 - Search Dry-Run Async Task Governance API/UI - Development

## Date
2026-03-08

## Goal
- 为 Advanced Search 的 dry-run CSV async export 任务补齐治理能力，和 Audit/Ops/Preview 任务中心保持一致。

## Implemented

### 1) Backend governance API
- Updated `ecm-core/src/main/java/com/ecm/core/controller/SearchController.java`
  - Enhanced list endpoint:
    - `GET /api/v1/search/preview/queue-failed/dry-run/export-async`
    - added optional `status`
  - Added summary endpoint:
    - `GET /api/v1/search/preview/queue-failed/dry-run/export-async/summary`
    - returns `total/queued/running/completed/cancelled/failed/terminal/active`
  - Added cleanup endpoint:
    - `POST /api/v1/search/preview/queue-failed/dry-run/export-async/cleanup`
    - default cleanup terminal tasks
    - optional `status` only allows terminal states
  - Invalid status now returns `400` with clear error message.

### 2) Backend security tests
- Updated `ecm-core/src/test/java/com/ecm/core/controller/SearchControllerSecurityTest.java`
  - Added USER/ADMIN coverage for:
    - list with status filter access control
    - summary endpoint access control
    - cleanup endpoint access control
    - invalid status/invalid cleanup status behavior

### 3) Frontend service + page
- Updated `ecm-frontend/src/services/nodeService.ts`
  - Added APIs:
    - `listDryRunFailedPreviewsCsvExportAsyncBySearchTasks(limit, status?)`
    - `getDryRunFailedPreviewsCsvExportAsyncBySearchTasksSummary(status?)`
    - `cleanupDryRunFailedPreviewsCsvExportAsyncBySearchTasks(status?)`
- Updated `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
  - Added dry-run async task status filter and summary chips.
  - Added cleanup button aligned with current filter.
  - list + summary refresh in same filter context.
  - active status cleanup guardrail with clear user feedback.

### 4) Mocked E2E updates
- Updated `ecm-frontend/e2e/advanced-search-preview-batch-scope.mock.spec.ts`
  - Added mocks for `/summary` and `/cleanup`.
  - list mock now supports `status` filtering.
  - Added assertions for:
    - summary/cleanup/list status-filter calls
    - UI feedback and post-cleanup empty-task state.

## Impact
- Search dry-run async export 不再只有创建/单条取消，已具备任务中心级别治理。
- 与其他 async export 域的操作模型统一，便于后续抽象共用组件和运维流程。

