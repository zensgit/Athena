# Phase 203 - Export Task Center Panel + Cancel Control - Development

## Date
2026-03-08

## Goal
- 继续对标 Alfresco 的任务化批处理运维能力，将 Phase201/202 的后端任务接口提升为“可视化任务中心”操作体验。
- 在 Advanced Search 的预览 dry-run 区域提供任务列表与精细化运维动作（刷新、下载、取消）。

## Implemented

### 1) Backend: task center API completed for preview dry-run exports
- Updated `ecm-core/src/main/java/com/ecm/core/controller/SearchController.java`:
  - Added list endpoint:
    - `GET /api/v1/search/preview/queue-failed/dry-run/export-async?limit=...`
  - Added cancel endpoint:
    - `POST /api/v1/search/preview/queue-failed/dry-run/export-async/{taskId}/cancel`
  - Async status payload enriched with `createdAt`.
  - Added terminal state `CANCELLED` and worker-side cancel guards so cancelled tasks are not overwritten by subsequent completion updates.

### 2) Backend tests
- Updated `ecm-core/src/test/java/com/ecm/core/controller/SearchControllerTest.java`:
  - Added list endpoint behavior test.
  - Added cancel behavior tests:
    - running task can be cancelled
    - cancelled task download returns `409`
    - unknown task cancel returns `404`
  - Existing async status assertion expanded with `createdAt`.
- Updated `ecm-core/src/test/java/com/ecm/core/controller/SearchControllerSecurityTest.java`:
  - Added security coverage for list/cancel endpoints (admin allow, user forbid).

### 3) Frontend: export task center panel in Advanced Search
- Updated `ecm-frontend/src/services/nodeService.ts`:
  - Added async export task APIs:
    - list tasks
    - cancel task
  - Extended task DTOs with `createdAt/finishedAt/filename`.
- Updated `ecm-frontend/src/pages/AdvancedSearchPage.tsx`:
  - Added in-flight export cancellation (`Cancel export`) for current task.
  - Added “Recent export tasks” panel with per-task actions:
    - `Refresh export tasks`
    - `Download` (for completed)
    - `Cancel` (for queued/running)
  - Improved user feedback:
    - cancellation yields info-level feedback, not generic error.

### 4) Mocked E2E alignment
- Updated `ecm-frontend/e2e/advanced-search-preview-batch-scope.mock.spec.ts`:
  - Existing export flow now mocks task-list calls.
  - Existing cancel flow adapted to task-list and cancel endpoint behavior.
