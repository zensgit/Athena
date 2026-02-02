# Phase 5 P1 Upload Processing Clear/Dismiss - Development

## Summary
Add controls to clear or dismiss items from the upload processing list.

## Scope
- Clear all uploaded items with a single action.
- Dismiss individual items from the list.

## Implementation
- Added a "Clear list" button in the uploaded items header.
- Added a per-item dismiss icon next to preview actions.
- Clearing resets refresh timestamps and queued state.

## Files Changed
- `ecm-frontend/src/components/dialogs/UploadDialog.tsx`
