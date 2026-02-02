# Phase 7 P1 Version History Diff Summary - Development

## Summary
Enhance the version history dialog with size delta summaries, confirmation dialogs, and audit hints.

## Scope
- Display per-version size delta relative to the previous version.
- Add explicit confirmation dialogs for download and restore actions.
- Surface an audit hint indicating actions are logged.

## Implementation
- Added a size delta helper with baseline/no-change handling.
- Added a confirmation dialog for download/restore actions.
- Added an informational audit notice at the top of the dialog.

## Files Changed
- `ecm-frontend/src/components/dialogs/VersionHistoryDialog.tsx`
