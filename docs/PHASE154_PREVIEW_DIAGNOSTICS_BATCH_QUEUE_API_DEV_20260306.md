# Phase 154: Preview Diagnostics Batch Queue API (Development)

## Date
2026-03-06

## Goal
- Provide a single admin API to queue preview retry/rebuild for multiple document ids in one call.

## Scope
- Backend only (`ecm-core`):
  - `POST /api/v1/preview/diagnostics/failures/queue-batch`
  - Request: `documentIds[]`, `force`
  - Response: requested/deduplicated/queued/skipped/failed + per-item outcome

## Design
1. Add endpoint in `PreviewDiagnosticsController` under existing admin protection.
2. Deduplicate input ids server-side (`LinkedHashSet`) and ignore null ids.
3. Reuse `PreviewQueueService.enqueue(id, force)` for consistent queue behavior.
4. Return per-item outcome:
   - `QUEUED`
   - `SKIPPED`
   - `FAILED`
5. Catch per-item exceptions and continue to avoid whole-batch failure.

## Changed Files
- `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`
- `ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java`
