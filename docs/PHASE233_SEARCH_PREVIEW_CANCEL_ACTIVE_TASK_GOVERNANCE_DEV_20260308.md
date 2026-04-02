# Phase 233 - Search + Preview Cancel-Active Task Governance - Development

## Date
2026-03-08

## Goal
- 补齐 Search dry-run 与 Preview rendition 两条 async export 链路的 `cancel-active` 治理能力。
- 让两条链路在 API、UI、测试覆盖上与 Audit/Ops 保持一致操作模型。

## Implemented

### 1) Backend governance API
- Updated `ecm-core/src/main/java/com/ecm/core/controller/SearchController.java`
  - Added:
    - `POST /api/v1/search/preview/queue-failed/dry-run/export-async/cancel-active`
  - Behavior:
    - default cancels all active tasks (`QUEUED`/`RUNNING`)
    - optional `status` only allows `QUEUED` or `RUNNING`
    - terminal status for this endpoint returns `400`
    - unknown status keeps existing `400 (Unknown async export status: ...)`
  - Added response DTO fields:
    - `cancelledCount`
    - `remainingActiveCount`
    - `statusFilter`
    - `message`

- Updated `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`
  - Added:
    - `POST /api/v1/preview/diagnostics/renditions/resources/export-async/cancel-active`
  - Behavior:
    - default cancels all active tasks (`QUEUED`/`RUNNING`)
    - optional `status` only allows `QUEUED` or `RUNNING`
    - terminal status filter returns `400`
    - unknown status keeps existing `400 (Unknown async export status: ...)`
  - Added response DTO fields:
    - `cancelledCount`
    - `remainingActiveCount`
    - `statusFilter`
    - `message`

### 2) Backend tests
- Updated `ecm-core/src/test/java/com/ecm/core/controller/SearchControllerSecurityTest.java`
  - added USER forbidden / ADMIN success / terminal status rejected cases for `cancel-active`.
- Updated `ecm-core/src/test/java/com/ecm/core/controller/SearchControllerTest.java`
  - added behavior tests for:
    - default cancel-active
    - `status=QUEUED`
    - terminal status rejected
    - unknown status rejected
- Updated `ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java`
  - added USER forbidden / ADMIN success / invalid + terminal status rejected cases.

### 3) Frontend service + UI
- Updated `ecm-frontend/src/services/nodeService.ts`
  - added search dry-run cancel-active types + API:
    - `cancelActiveDryRunFailedPreviewsCsvExportAsyncBySearchTasks(status?)`
- Updated `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
  - added task-center action:
    - `Cancel active tasks`
    - `aria-label="Cancel active export tasks"`
  - forwards `status` only when current filter is `QUEUED`/`RUNNING`.
  - refreshes list + summary after action.

- Updated `ecm-frontend/src/services/previewDiagnosticsService.ts`
  - added rendition cancel-active types + API:
    - `cancelActiveRenditionResourcesExportTasks(status?)`
- Updated `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
  - added rendition task-center action:
    - `Cancel active tasks`
    - `aria-label="Cancel active rendition export tasks"`
  - forwards `status` only when current filter is `QUEUED`/`RUNNING`.
  - refreshes list + summary after action.

### 4) Mocked E2E updates
- Updated `ecm-frontend/e2e/advanced-search-preview-batch-scope.mock.spec.ts`
  - added `/search/.../export-async/cancel-active` mock route and assertions for filter forwarding.
- Updated `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`
  - added `/preview/diagnostics/.../export-async/cancel-active` mock route and UI assertions.
  - fixed route handling to avoid mixing rendition route variables with ops route variables.

## Impact
- Search + Preview 两条任务中心都具备批量取消活动任务能力。
- `summary/cleanup/cancel-active` 在 async export 治理模型上实现跨域一致，便于后续统一任务中心抽象。
