# Phase 201 - Search-Scope Dry-Run CSV Async Export Task - Development

## Date
2026-03-08

## Goal
- 对标 Alfresco 的批处理/后台任务模型，把搜索范围 dry-run CSV 导出从同步请求升级为异步任务流。
- 降低大结果集导出对前端请求超时和阻塞的风险，并提供可轮询状态与可下载结果。

## Implemented

### 1) Backend: async export task API (admin only)
- Updated `ecm-core/src/main/java/com/ecm/core/controller/SearchController.java`:
  - Added endpoints:
    - `POST /api/v1/search/preview/queue-failed/dry-run/export-async`
      - start task, returns `taskId/status/createdAt`
    - `GET /api/v1/search/preview/queue-failed/dry-run/export-async/{taskId}`
      - query task status, returns `status/error/finishedAt/filename`
    - `GET /api/v1/search/preview/queue-failed/dry-run/export-async/{taskId}/download`
      - download CSV attachment when task completed
  - Added in-memory async task registry with thread-safe lifecycle:
    - status: `QUEUED/RUNNING/COMPLETED/FAILED`
    - bounded retention: keep up to 100 tasks, evict oldest completed/failed tasks first
  - Reused existing dry-run matcher + CSV builder to keep export payload consistent with Phase200 sync export.

### 2) Backend tests
- Updated `ecm-core/src/test/java/com/ecm/core/controller/SearchControllerTest.java`:
  - Added async export happy-path test:
    - create task -> poll terminal status -> download CSV
  - Added conflict-path tests:
    - download before completion -> `409`
    - task failed -> status `FAILED`, download `409`
- Updated `ecm-core/src/test/java/com/ecm/core/controller/SearchControllerSecurityTest.java`:
  - Added admin/security coverage for async start/status/download endpoints.

### 3) Frontend: export flow upgraded to async task polling
- Updated `ecm-frontend/src/services/nodeService.ts`:
  - Added async export APIs:
    - start task
    - get task status
    - download task result (blob)
- Updated `ecm-frontend/src/pages/AdvancedSearchPage.tsx`:
  - `Export dry-run CSV` now:
    - starts async task
    - polls status every 1s (max 30 attempts)
    - auto-downloads on completion
    - shows phase text: `Preparing CSV export...` / `Running CSV export...` / `Downloading CSV...`
    - surfaces backend error message when task fails

### 4) E2E mock alignment
- Updated `ecm-frontend/e2e/advanced-search-preview-batch-scope.mock.spec.ts`:
  - Replaced sync export mock with async start/status/download mocks.
  - Added assertions for:
    - start payload propagation (`query/reason/maxDocuments/filters.previewStatuses`)
    - status polling count
    - taskId consistency between status and download calls
