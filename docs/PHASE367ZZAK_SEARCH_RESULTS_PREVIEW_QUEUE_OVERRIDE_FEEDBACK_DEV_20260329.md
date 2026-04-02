# Phase367ZZAK Search Results Preview Queue Override Feedback

## Goal

Make ordinary search result preview queue actions preserve richer queue feedback on the current page instead of collapsing back to a minimal local status override.

## Problem

`SearchResults.tsx` already kept a local `previewQueueStatusById` map after `queuePreview(...)`, but the override shape only supported the minimum fields needed for status chips. That left three operator gaps:

1. The current-page result cards dropped `previewLastUpdated`.
2. Queue feedback lost a queue-state concept and the server message.
3. Retry actions used ad-hoc inline override objects instead of a shared local projection helper.

This made ordinary search weaker than the richer preview queue feedback already available in other surfaces.

## Implementation

### Shared queue override mapper

Added `ecm-frontend/src/utils/previewQueueOverrideUtils.ts`:

- Defines `PreviewQueueOverride`.
- Maps `PreviewQueueStatus` into a stable local override payload.
- Preserves:
  - `previewStatus`
  - `previewFailureReason`
  - `previewFailureCategory`
  - `previewLastUpdated`
  - `attempts`
  - `nextAttemptAt`
  - `queueState`
  - `message`

### Search results state convergence

Updated `ecm-frontend/src/pages/SearchResults.tsx`:

- `previewQueueStatusById` now stores `PreviewQueueOverride`.
- Added `applyPreviewQueueStatusOverride(nodeId, status)` helper.
- `handleRetryPreview(...)`, `handleRetryFailedPreviews(...)`, and `handleRetryFailedReason(...)` now all use the shared helper.
- Queue detail tooltip now includes:
  - queue state
  - attempts
  - next retry time
  - preview updated time
  - server queue message

## Outcome

Ordinary search results now keep richer preview queue feedback on the current page after queue actions. This reduces the gap between search results and the stronger operator feedback already present in advanced/admin preview flows.
