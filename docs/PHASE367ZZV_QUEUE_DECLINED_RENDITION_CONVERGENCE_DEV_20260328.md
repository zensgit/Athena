# Phase367ZZV Queue Declined Rendition Convergence

## Goal

Continue moving preview diagnostics admin surfaces away from raw queue snapshot preview status and toward rendition-backed effective preview semantics for the `queue-declined` line.

## Problem

After converging failure samples, queue summary, dead-letter, and prevention payloads, `queue-declined` still had a separate semantics path:

- `GET /api/v1/preview/diagnostics/queue/declined`
- `GET /api/v1/preview/diagnostics/queue/declined/export`
- `POST /api/v1/preview/diagnostics/queue/declined/requeue`
- `POST /api/v1/preview/diagnostics/queue/declined/requeue/dry-run`
- related async CSV exports built from the same mapped items

These flows still started from `PreviewQueueService.PreviewQueueDeclinedItem.previewStatus`, which could lag behind the newer rendition-backed effective preview semantics already used elsewhere in diagnostics.

## Design

### Use a fallback-aware effective preview helper

File: `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`

Extend:

- `resolveEffectivePreviewStatus(Document document)`

to delegate to:

- `resolveEffectivePreviewStatus(Document document, String fallbackPreviewStatus)`

Behavior:

1. if no document is available, fall back to the queue snapshot status
2. otherwise prefer `renditionResourceService.summarizeDocument(document).previewStatus`
3. if rendition summary has no status, fall back to raw `Document.previewStatus`
4. if neither exists, use the queue snapshot status

### Apply it at the queue-declined mapping boundary

Update `mapQueueDeclinedItems(...)` so `PreviewQueueDeclinedItemDto.previewStatus` is resolved from:

- document-backed effective rendition semantics when a matching `Document` exists
- raw declined snapshot status only when no document-backed semantics are available

This is the right boundary because the same mapped DTOs feed:

- live queue-declined summary
- queue-declined CSV export
- queue-declined async export
- requeue action fallback preview status
- requeue dry-run fallback preview status

### Keep action DTO shapes stable

No public DTO shape changes were introduced. `requeue` and `dry-run` continue to use the existing `previewStatus` field, but that field is now fed by the effective queue-declined mapping instead of the raw queue snapshot alone.

## Result

`queue-declined` is no longer a separate preview-status island inside preview diagnostics. Live summary, CSV export, requeue, dry-run, and async export now all inherit the same rendition-backed effective preview semantics used by the rest of the admin diagnostics surfaces.
