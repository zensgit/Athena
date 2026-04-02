# Phase368ZL Upload Dialog Local Override Queue Contract Convergence

## Goal

Remove the `UploadDialog` double source of truth for preview queue feedback by projecting queue overrides exactly once instead of patching `uploadedItems` and then hand-merging the same fields again at render time.

## Problem

`UploadDialog` stored queue mutation feedback in `previewQueueStatusById`, but `handleQueuePreview(...)` also patched `uploadedItems` directly. Rendering then re-merged queue state in several places:

- preview chip meta
- status summary
- uploaded item row rendering

That duplicated the same preview queue contract merge logic already being standardized elsewhere.

## Implementation

### Shared upload override helper

Added `uploadDialogPreviewQueueOverrideUtils.ts` with `applyUploadPreviewQueueLocalOverrides(...)`.

The helper projects these fields from `uploadedItems + previewQueueStatusById`:

- `previewStatus`
- `previewFailureReason`
- `previewFailureCategory`
- `previewLastUpdated`
- `queueState`
- `attempts`
- `nextAttemptAt`
- `message`

### Upload dialog changes

- `handleQueuePreview(...)` now writes only `previewQueueStatusById`
- added `previewAwareUploadedItems` memoized projection
- `getPreviewStatusMeta(...)`, status summary aggregation, and uploaded list row rendering now consume the projected items instead of hand-merging queue status repeatedly

## Result

`UploadDialog` now follows the same queue override convergence pattern as the other preview governance surfaces, with one local projection layer and no duplicate preview state mutation inside the raw uploaded item list.
