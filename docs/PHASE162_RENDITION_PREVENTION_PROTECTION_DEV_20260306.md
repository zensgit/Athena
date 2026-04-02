# Phase162 Dev: Rendition Prevention and Protection

## Date
2026-03-06

## Goal
Add Day6 protection mechanics so repeated futile preview queue requests are blocked, observable, and recoverable with one-click admin actions.

## Borrowed pattern from Alfresco
- `RenditionPreventionRegistry`:
  - keep a dedicated prevention marker registry for known futile rendition attempts.
  - provide operator-controlled unblock + reprocess flow.

## Backend changes
- Added prevention registry:
  - `ecm-core/src/main/java/com/ecm/core/preview/PreviewRenditionPreventionRegistry.java`
  - in-memory bounded blocked map with:
    - block/unblock/get/list
    - block-hit counter (`markBlockedHit`)
    - auto-block category controls (`UNSUPPORTED`, `PERMANENT` by default)
- Queue integration:
  - `ecm-core/src/main/java/com/ecm/core/preview/PreviewQueueService.java`
  - enqueue gate:
    - skip non-force queue when doc is blocked
    - increment blocked hit count to expose queue storm pressure
    - force enqueue auto-unblocks first
  - completion behavior:
    - auto-block unsupported/permanent terminal failures
    - unblock on successful supported result
- Admin diagnostics APIs:
  - `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`
  - added endpoints (admin only):
    - `GET /api/v1/preview/diagnostics/prevention/blocked`
    - `POST /api/v1/preview/diagnostics/prevention/{documentId}/unblock`
    - `POST /api/v1/preview/diagnostics/prevention/{documentId}/unblock-requeue?force=true|false`
  - response includes blocked item metadata (name/path/mime/status/category/reason/hits/timestamps).
- Config bridge:
  - `ecm-core/src/main/resources/application.yml`
  - added:
    - `ecm.preview.prevention.enabled`
    - `ecm.preview.prevention.max-blocked`
    - `ecm.preview.prevention.auto-block-categories`

## Frontend changes
- API service:
  - `ecm-frontend/src/services/previewDiagnosticsService.ts`
  - added:
    - `getRenditionPreventionBlocked(...)`
    - `unblockRenditionPrevention(...)`
    - `unblockAndRequeueRendition(...)`
- Diagnostics page:
  - `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
  - added panel: `Rendition Prevention Registry`
    - blocked count and auto-block category chips
    - blocked entry table with hit counters and timestamps
    - row actions:
      - `Unblock`
      - `Requeue` (unblock + queue)

## Test updates
- Added:
  - `ecm-core/src/test/java/com/ecm/core/preview/PreviewRenditionPreventionRegistryTest.java`
- Updated:
  - `ecm-core/src/test/java/com/ecm/core/preview/PreviewQueueServiceTest.java`
    - blocked skip + hit counting
    - force enqueue auto-unblock
    - unsupported terminal outcome auto-block
  - `ecm-core/src/test/java/com/ecm/core/preview/PreviewQueueServiceRedisBackendTest.java`
    - constructor dependency alignment
  - `ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java`
    - prevention endpoint security and admin behavior coverage
  - `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`
    - prevention panel mock/assertions + unblock/requeue action flow
