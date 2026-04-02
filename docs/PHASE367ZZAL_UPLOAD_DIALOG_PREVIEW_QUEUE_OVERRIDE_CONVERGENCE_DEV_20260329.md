# Phase367ZZAL Upload Dialog Preview Queue Override Convergence

## Goal

Bring the upload work surface up to the same richer local preview queue feedback used in ordinary and advanced search.

## Problem

`UploadDialog.tsx` already called `queuePreview(...)`, but the current-page upload list still risked losing richer queue feedback after a queue action:

- `previewLastUpdated`
- queue-state semantics
- queue message

That made upload a weaker operator surface than search-oriented preview retry flows.

## Implementation

Updated `ecm-frontend/src/components/dialogs/UploadDialog.tsx` to consume the shared `PreviewQueueOverride` shape from `previewQueueOverrideUtils.ts`.

### Local override convergence

- `previewQueueStatusById` is now preserved across queue actions.
- `queuePreview(...)` writes `buildPreviewQueueOverride(status)` into the local map.
- dismissing or clearing uploaded items also clears matching preview queue overrides.
- refreshing uploaded items clears local overrides for refreshed rows so stale local queue feedback does not outlive a server refresh.

### Operator feedback

- Preview chips now resolve through the local queue override when available.
- Tooltip feedback includes queue-state and preview-updated details when present.
- Current-page upload list continues to show effective preview status immediately after queue actions instead of waiting for a full refresh.

## Outcome

Upload now participates in the same local preview queue feedback model as other high-frequency preview operator surfaces, reducing another preview/rendition semantics split.
