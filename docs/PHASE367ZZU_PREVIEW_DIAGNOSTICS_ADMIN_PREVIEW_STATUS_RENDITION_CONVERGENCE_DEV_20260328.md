# Phase367ZZU Preview Diagnostics Admin Preview Status Rendition Convergence

## Goal

Continue moving admin preview diagnostics payloads away from raw `Document.previewStatus` and toward rendition-backed effective preview semantics.

## Problem

After converging failure samples, several admin payloads still exposed `previewStatus` directly from `Document.previewStatus`:

- `GET /api/v1/preview/diagnostics/prevention/blocked`
- `GET /api/v1/preview/diagnostics/dead-letter`
- `GET /api/v1/preview/diagnostics/queue/summary`
- `GET /api/v1/preview/diagnostics/dead-letter/export`

This meant admin operators could still see stale or less accurate preview states in prevention, dead-letter, and queue diagnostics, even when `RenditionResourceService` already knew the effective preview state.

## Design

### Shared helper

File: `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`

Add:

- `resolveEffectivePreviewStatus(Document document)`

Behavior:

1. call `renditionResourceService.summarizeDocument(document)`
2. if summary is document-backed and has a preview status, return it
3. otherwise fall back to raw `Document.previewStatus`

### Apply helper to admin payloads

Replace raw status extraction in:

- blocked rendition prevention item mapping
- dead-letter item mapping
- preview queue diagnostics item mapping
- dead-letter CSV export rows

No DTO shapes changed. Only the source of `previewStatus` changed.

## Result

Admin operators now see preview status values in prevention, dead-letter, queue diagnostics, and dead-letter export that align better with the newer rendition-backed semantics used elsewhere in Athena.
