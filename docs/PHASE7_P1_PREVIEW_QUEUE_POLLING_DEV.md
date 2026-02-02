# Phase 7 P1 Preview Queue Polling - Development

## Summary
Add preview queue polling and retry guidance to the document preview experience.

## Scope
- Auto-poll preview status while queued or processing.
- Display retry guidance and actions when preview fails.

## Implementation
- Added a polling interval that refreshes `/documents/{id}/preview` when status is PROCESSING/QUEUED.
- Added informational and warning alerts to guide retry/force rebuild actions.

## Files Changed
- `ecm-frontend/src/components/preview/DocumentPreview.tsx`
