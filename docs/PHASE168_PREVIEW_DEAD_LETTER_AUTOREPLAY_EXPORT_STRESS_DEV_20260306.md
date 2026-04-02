# Phase168 Dev: Preview Dead-Letter Auto-Replay, Export, and Contention Hardening

## Date
2026-03-06

## Goal
Complete the dead-letter operator loop with:
- automatic replay policy (category + cooldown + batch cap).
- CSV export for offline triage/reporting.
- contention/idempotency stress validation for Redis queue enqueue path.

## Backend changes
- `ecm-core/src/main/java/com/ecm/core/preview/PreviewQueueService.java`
  - add scheduled dead-letter auto replay:
    - `@Scheduled(fixedDelayString = "${ecm.preview.dead-letter.auto-replay.poll-interval-ms:30000}")`
  - policy controls:
    - `enabled`
    - `max-items`
    - `cooldown-ms`
    - `force`
    - `categories`
  - replay behavior:
    - only replay entries in allowed categories and outside cooldown.
    - keep dead-letter entry for skipped/failed replay and mark replay attempts for cooldown tracking.
- `ecm-core/src/main/java/com/ecm/core/preview/PreviewDeadLetterRegistry.java`
  - extend dead-letter entry metadata:
    - `lastReplayAt`
    - `replayCount`
  - add `markReplayAttempt(...)` for memory + Redis backends.
  - keep Redis serialization backward compatible with older 7-field payload.
- `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`
  - add `GET /api/v1/preview/diagnostics/dead-letter/export`.
  - CSV columns:
    - document identity, mime/status, category/policy/stage, attempts/occurrences/replayCount, failedAt/lastReplayAt, reason.
  - add audit events:
    - `PREVIEW_DEAD_LETTER_EXPORTED`
    - `PREVIEW_DEAD_LETTER_REPLAY`
- `ecm-core/src/main/resources/application.yml`
  - add dead-letter auto replay config bridge:
    - `ecm.preview.dead-letter.auto-replay.enabled`
    - `ecm.preview.dead-letter.auto-replay.poll-interval-ms`
    - `ecm.preview.dead-letter.auto-replay.max-items`
    - `ecm.preview.dead-letter.auto-replay.cooldown-ms`
    - `ecm.preview.dead-letter.auto-replay.force`
    - `ecm.preview.dead-letter.auto-replay.categories`

## Frontend changes
- `ecm-frontend/src/services/previewDiagnosticsService.ts`
  - add `exportDeadLetterCsv(limit)` using existing authenticated download flow.
  - dead-letter item type now includes optional replay metadata.
- `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
  - add dead-letter panel action button:
    - `Export CSV`
- `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`
  - add dead-letter export mock response and assertion.
  - validate export action toast and request path coverage.

## Test additions/updates
- `ecm-core/src/test/java/com/ecm/core/preview/PreviewQueueServiceTest.java`
  - auto-replay filters by category/cooldown.
  - skipped replay path updates replay attempt marker.
- `ecm-core/src/test/java/com/ecm/core/preview/PreviewQueueServiceRedisBackendTest.java`
  - concurrent enqueue stress test verifies single Redis scheduled member under contention.
- `ecm-core/src/test/java/com/ecm/core/preview/PreviewDeadLetterRegistryTest.java`
  - replay metadata update coverage.
- `ecm-core/src/test/java/com/ecm/core/preview/PreviewDeadLetterRegistryRedisBackendTest.java`
  - replay metadata persists in Redis backend.
- `ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java`
  - dead-letter export endpoint (admin + forbidden).
  - audit-event assertions for replay and export.
