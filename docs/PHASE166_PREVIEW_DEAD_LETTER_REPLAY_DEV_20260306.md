# Phase166 Dev: Preview Dead-Letter Replay Loop

## Date
2026-03-06

## Goal
Build an operator recovery loop for terminal preview failures:
- capture terminal failures into a dead-letter registry.
- expose admin diagnostics API for dead-letter entries.
- support one-click/batch replay from dead-letter back into preview queue.

## Backend changes
- `ecm-core/src/main/java/com/ecm/core/preview/PreviewDeadLetterRegistry.java`
  - new dead-letter registry component.
  - supports:
    - `record(...)`
    - `remove(...)`
    - `list(limit)`
  - entry metadata:
    - `reason`, `category`, `policyKey`, `sourceStage`, `failedAt`, `attempts`, `occurrences`
- `ecm-core/src/main/java/com/ecm/core/preview/PreviewQueueService.java`
  - integrated dead-letter lifecycle:
    - terminal failure / retry-exhausted -> `record`
    - queue accepted / successful preview -> `remove`
  - applies to memory + redis queue backend paths.
- `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`
  - new endpoints:
    - `GET /api/v1/preview/diagnostics/dead-letter`
    - `POST /api/v1/preview/diagnostics/dead-letter/replay-batch`
  - response includes dead-letter registry stats and detailed item rows.
- `ecm-core/src/main/resources/application.yml`
  - added config:
    - `ecm.preview.dead-letter.enabled`
    - `ecm.preview.dead-letter.max-entries`

## Frontend changes
- `ecm-frontend/src/services/previewDiagnosticsService.ts`
  - added dead-letter DTOs + API methods:
    - `getDeadLetter(...)`
    - `replayDeadLetterBatch(...)`
- `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
  - added **Preview Dead Letter Queue** panel:
    - list + filter + select-all + selected count
    - per-row replay and batch replay
    - enabled/count/max chips
    - replay result toasts and unified refresh
- `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`
  - mocked dead-letter endpoints and UI assertions for replay flow.

## Test updates
- `ecm-core/src/test/java/com/ecm/core/preview/PreviewDeadLetterRegistryTest.java`
  - registry record/update/trim/remove coverage.
- `ecm-core/src/test/java/com/ecm/core/preview/PreviewQueueServiceTest.java`
  - dead-letter record on retry exhaustion.
  - dead-letter removal on successful replay.
- `ecm-core/src/test/java/com/ecm/core/preview/PreviewQueueServiceRedisBackendTest.java`
  - constructor/dependency update for dead-letter integration.
- `ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java`
  - admin/forbidden coverage for dead-letter endpoints.

## Delivery gate update
- `scripts/phase164-preview-day7-delivery-gate.sh`
  - backend targeted test set now includes `PreviewDeadLetterRegistryTest`.
