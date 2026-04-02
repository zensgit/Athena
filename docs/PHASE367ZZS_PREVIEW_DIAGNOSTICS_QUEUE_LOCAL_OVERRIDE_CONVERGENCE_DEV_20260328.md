# Phase367ZZS Preview Diagnostics Queue Local Override Convergence

## Goal

Make `PreviewDiagnosticsPage` consume the richer `queuePreview(...)` response as a temporary local source of truth, so the failure table stops lagging behind row-level queue actions.

## Problem

After `Phase367ZZP`, `queuePreview(...)` already returned:

- `previewStatus`
- `previewFailureReason`
- `previewFailureCategory`
- `previewLastUpdated`
- queue metadata and message

But `PreviewDiagnosticsPage` still treated queueing as fire-and-forget:

- it ignored the richer response body
- it rendered the failure table from raw `PreviewFailureSample` values only
- the `Status` column still showed failure-only labels even when queueing had already moved the item to `QUEUED` or `PROCESSING`
- page-level summary/filter matching also stayed on stale values until the full reload completed

## Design

### Local queue override map

File: `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`

Add a narrow local state:

- `previewQueueStatusById`

with:

- `previewStatus`
- `previewFailureReason`
- `previewFailureCategory`
- `previewLastUpdated`

This state is only used as a short-lived UI override between:

1. queue action completion
2. successful `loadFailures()` refresh

The override map is cleared on successful refresh and on refresh failure.

### Preview-aware item projection

Build `previewAwareItems` by overlaying queue-local preview fields onto `items`.

Then move these consumers to `previewAwareItems`:

- failed preview summary
- text filter matching
- reason matching in current list
- failure table row rendering

### Effective status rendering

The failure table `Status` column now uses `getEffectivePreviewStatus(...)` plus existing failed-preview metadata rules:

- `READY` -> `Preview ready`
- `PROCESSING` -> `Preview processing`
- `QUEUED` -> `Preview queued`
- `PENDING` -> `Preview pending`
- `FAILED / UNSUPPORTED` -> existing failed-preview labels/colors

This keeps the diagnostics table aligned with the same effective preview semantics already used elsewhere.

### Queue action feedback

`handleQueuePreview(...)` now:

- stores the richer response into the local override map
- surfaces queue response messaging more accurately
- keeps the existing full `loadFailures()` refresh

## Result

`PreviewDiagnosticsPage` no longer behaves like a stale failure-only surface immediately after queueing a row. The admin table, its summary, and its current-list reason matching all move to the effective preview state before the reload completes.
