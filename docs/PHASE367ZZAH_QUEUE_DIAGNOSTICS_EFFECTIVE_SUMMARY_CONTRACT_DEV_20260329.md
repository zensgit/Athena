# Phase 367ZZAH: Queue Diagnostics Effective Summary Contract

## Goal

Make preview queue diagnostics use the same effective preview summary contract across:

- `GET /api/v1/preview/diagnostics/queue/summary`
- `GET /api/v1/preview/diagnostics/queue/summary/export`
- the queue diagnostics table and queue-declined main table in `PreviewDiagnosticsPage`

The intent is to stop treating queue diagnostics as a separate raw `Document.preview*` surface.

## Scope

### Backend

Files:

- `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`
- `ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java`

Changes:

- Extended `PreviewQueueDiagnosticsItemDto` to carry:
  - `previewFailureReason`
  - `previewFailureCategory`
  - `previewLastUpdated`
- Updated queue diagnostics mapping to use `resolveEffectivePreviewSnapshot(...)`
- Extended queue diagnostics CSV export columns to include effective failure summary fields
- Extended queue diagnostics query matching to search over failure reason/category
- Added null-safe queue diagnostics snapshot loading for summary/export/cancel-active paths so export no longer hard-fails when the queue service returns no snapshot

### Frontend

Files:

- `ecm-frontend/src/services/previewDiagnosticsService.ts`
- `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`

Changes:

- Extended `PreviewQueueDiagnosticsItem` typing with effective failure summary fields
- Updated the queue-declined main table to display:
  - preview failure category chip
  - preview failure reason
  - preview last updated

## Outcome

Queue diagnostics and queue-declined now expose the same effective preview semantics that were already being pushed into search, node payloads, preview queue, and rendition-backed surfaces.
