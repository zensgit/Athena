# Phase 5 P1 Upload Processing Queue Actions - Development

## Summary
Add per-item preview queue controls in the upload processing list for items that are not ready.

## Scope
- Show "Queue preview" and "Force rebuild" actions for non-ready document previews.
- Update preview status optimistically after queueing.
- Use the existing preview queue endpoint used elsewhere in the app.

## Implementation
- Added a small `PreviewQueueStatus` type and a queue handler in the upload dialog.
- Queue actions call `POST /documents/{id}/preview/queue` with optional `force`.
- Preview status chips now treat missing status as "Preview pending".

## Files Changed
- `ecm-frontend/src/components/dialogs/UploadDialog.tsx`
