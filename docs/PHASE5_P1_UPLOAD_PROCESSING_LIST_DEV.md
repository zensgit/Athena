# Phase 5 P1 Upload Processing List - Development

## Summary
Expose uploaded items inside the upload dialog and surface preview-processing status with a manual refresh control.

## Scope
- Upload dialog shows a running list of successfully uploaded nodes.
- Each item displays a preview status chip and any failure reason.
- Manual refresh pulls the latest node status from the API.

## Implementation
- Track uploaded nodes in dialog state (`uploadedItems`).
- When `uploadDocument` resolves, upsert the returned node into the list.
- Add a "Refresh status" button that calls `nodeService.getNode` for each item and replaces the list.
- Add a preview status helper to map `previewStatus` to chip label and color.

## Files Changed
- `ecm-frontend/src/components/dialogs/UploadDialog.tsx`

## Notes
- The list appears once an upload summary exists or at least one uploaded item is tracked.
- When no items are present, a placeholder message explains the expected behavior.
