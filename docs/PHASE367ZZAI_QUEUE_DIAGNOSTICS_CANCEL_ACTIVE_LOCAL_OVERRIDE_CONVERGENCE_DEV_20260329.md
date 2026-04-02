# Phase 367ZZAI: Queue Diagnostics Cancel-Active Local Override Convergence

## Goal

Remove the operator feedback gap after `Cancel Filtered` in preview queue diagnostics.

Before this slice, the user had to wait for a full `loadFailures()` refresh before the queue diagnostics table showed `CANCEL_REQUESTED`.

## Scope

Files:

- `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
- `ecm-frontend/src/utils/previewQueueDiagnosticsUtils.ts`
- `ecm-frontend/src/utils/previewQueueDiagnosticsUtils.test.ts`

## Changes

- Added shared helper `applyQueueCancelActiveResultToQueueDiagnosticsSummary(...)`
- Projected `cancelQueueDiagnosticsActive(...)` results back onto the current queue diagnostics summary immediately
- Recomputed:
  - `runningCount`
  - `cancellationRequestedCount`
  - row-level `queueState / running / cancelRequested`

## Outcome

`Cancel Filtered` now behaves like the newer rendition and queue-declined operator surfaces:

- immediate local feedback
- then background reconciliation via `loadFailures()`

This closes another admin workflow detail gap without touching the hotter preview/rendition contract files.
