# Phase 216 - Preview Rendition Resources Async Export Task Center - Development

## Date
2026-03-08

## Goal
- 将 Preview 资源导出从“同步下载”升级为“异步任务中心”，支持启动、查询、取消、下载。
- 与 Search Dry-run Async Export 的交互范式保持一致，降低运维学习成本。

## Implemented

### 1) Backend async export task APIs
- Updated `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`
  - Added async task storage:
    - `renditionResourcesExportAsyncTasks` (`ConcurrentHashMap`)
    - `renditionResourcesExportAsyncTaskOrder` (`Deque`)
  - Added endpoints:
    - `POST /api/v1/preview/diagnostics/renditions/resources/export-async`
    - `GET /api/v1/preview/diagnostics/renditions/resources/export-async`
    - `GET /api/v1/preview/diagnostics/renditions/resources/export-async/{taskId}`
    - `POST /api/v1/preview/diagnostics/renditions/resources/export-async/{taskId}/cancel`
    - `GET /api/v1/preview/diagnostics/renditions/resources/export-async/{taskId}/download`
  - Added async lifecycle helpers:
    - request snapshot copy / task create / task run / task list / task trim
  - Added status model:
    - `QUEUED`, `RUNNING`, `COMPLETED`, `CANCELLED`, `FAILED`
  - Task execution reuses existing CSV generator to ensure exported format consistency.

### 2) Backend security coverage
- Updated `ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java`
  - Added `USER` forbidden checks for all 5 async export endpoints.
  - Added `ADMIN` flow checks:
    - start returns `taskId/status`
    - list/status readable
    - cancel returns updated state
    - download returns conflict when task not completed

### 3) Frontend task center integration
- Updated `ecm-frontend/src/services/previewDiagnosticsService.ts`
  - Added async task DTOs.
  - Added APIs:
    - `startRenditionResourcesExportTask`
    - `listRenditionResourcesExportTasks`
    - `getRenditionResourcesExportTask`
    - `cancelRenditionResourcesExportTask`
    - `downloadRenditionResourcesExportTask`

- Updated `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
  - Added task center state (`renditionExportTasks`, loading, action state).
  - Added actions:
    - `Start Async Export`
    - `Refresh export tasks`
    - row `Cancel` / `Download`
  - Added task table:
    - `Task ID / Status / Created / Finished / Filename / Actions`
  - Kept existing sync export (`Export Resources CSV`) unchanged for backward compatibility.

### 4) Mocked E2E extension
- Updated `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`
  - Added route mocks for async task center endpoints.
  - Added UI assertions covering:
    - start async task
    - list/refresh and task visibility
    - download completed task
    - cancel queued task
  - Fixed strict locator ambiguity by using exact cell match for task-id assertion.

## Impact
- Preview 资源导出具备可观测、可取消、可追踪的任务中心能力，运维在大窗口导出时不再阻塞在单次同步下载。
- 与既有 async export 模型保持一致，便于统一后续的任务平台化（重试、清理、审计、配额）。
