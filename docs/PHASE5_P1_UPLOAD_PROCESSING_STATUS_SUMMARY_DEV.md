# Phase 5 P1 Upload Processing Status Summary - Development

## Summary
Add status summary chips and last-updated metadata to the upload processing list.

## Scope
- Show a total count and per-status counts (ready, pending, processing, queued, failed).
- Display a "Last updated" timestamp for the processing status list.

## Implementation
- Derived status counts from the uploaded items list.
- Updated refresh logic to track the latest refresh timestamp.
- Set the timestamp when new items are added or refreshed.

## Files Changed
- `ecm-frontend/src/components/dialogs/UploadDialog.tsx`
