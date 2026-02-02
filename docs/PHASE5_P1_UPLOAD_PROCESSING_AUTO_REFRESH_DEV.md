# Phase 5 P1 Upload Processing Auto Refresh - Development

## Summary
Add an auto-refresh toggle for the upload processing list and persist the preference locally.

## Scope
- Toggle auto-refresh for uploaded item status updates.
- Persist the toggle state in local storage.
- Auto-refresh only when the dialog is open and there are uploaded items.

## Implementation
- Added local storage key `ecmUploadAutoRefresh` with a default of enabled.
- Added a `Switch` control in the uploaded items header.
- Interval refresh reuses the manual refresh handler with a 15s cadence.

## Files Changed
- `ecm-frontend/src/components/dialogs/UploadDialog.tsx`
