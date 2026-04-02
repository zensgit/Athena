# Phase367ZW Document Preview Rendition Summary Consumption

## Goal

Move the single-document preview surface onto rendition-backed state without reopening the whole search or diagnostics pipeline.

This phase makes `DocumentPreview` prefer node rendition summary data over raw `node.previewStatus`, so the preview dialog begins consuming the same normalized rendition semantics as the converged node relation APIs.

## Why This Slice

- `DocumentPreview` is a high-value operator surface.
- It was still deriving status primarily from `node.previewStatus`, preview queue overrides, and the server preview payload.
- The backend already exposes a converged rendition summary via `GET /api/v1/nodes/{nodeId}/relations/renditions/summary`.
- Reusing that summary in the preview dialog is a low-conflict way to extend rendition-resource semantics into a real user-facing workflow.

## Implementation

### 1. `DocumentPreview` now loads rendition summary with other node detail metadata

`loadNodeDetails()` now fetches:

- node details
- lock info
- checkout info
- checkout graph
- node rendition relation summary

The preview dialog stores this as `previewRenditionSummary` and uses it as the preferred baseline source for:

- preview status
- preview failure reason
- preview failure category

Priority order now is:

1. transient preview override from queue/server preview actions
2. preview queue status
3. rendition summary
4. raw node payload
5. server preview payload fallback

### 2. Preview status is normalized through shared utility logic

`DocumentPreview` now uses `getEffectivePreviewStatus(...)` instead of manually trusting the raw status string.

This allows the preview dialog to understand rendition-backed states better, especially:

- `REGISTERED -> PENDING`
- `FAILED + unsupported classification -> UNSUPPORTED`

### 3. Preview alert logic now handles unsupported and pending semantics more cleanly

The preview toolbar and alert strip now:

- treat `UNSUPPORTED` as a real preview failure surface instead of silently skipping the alert
- treat `REGISTERED` as `PENDING` for the UI
- keep retry actions restricted to genuinely retryable failed states

## Files

- `ecm-frontend/src/components/preview/DocumentPreview.tsx`
- `ecm-frontend/src/utils/previewStatusUtils.ts`
- `ecm-frontend/src/utils/previewStatusUtils.test.ts`

## Behavioral Impact

- `DocumentPreview` is less dependent on stale raw `node.previewStatus`.
- Pending rendition resources no longer appear as a blank or confusing state in the preview dialog.
- Unsupported preview failures now show up consistently in the preview dialog's status/alert logic.

## Compatibility

- No route changes.
- No backend contract changes.
- This is a consumer convergence change on top of existing node rendition summary APIs.

## Follow-up

This phase intentionally does not touch:

- `SearchResults`
- search indexing
- preview diagnostics bulk surfaces

Those still rely on raw or indexed `previewStatus` semantics and should be migrated separately to avoid conflating UI convergence with index/model work.
