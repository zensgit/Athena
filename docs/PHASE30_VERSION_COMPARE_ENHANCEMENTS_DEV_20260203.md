# Phase 30 Version Compare Enhancements (2026-02-03)

## Goal
Improve version history comparison and context menu usability.

## Changes
- Added "Compare with current" action in version context menu.
- Extended compare dialog with:
  - Major flag
  - Size delta
  - Hash changed indicator
  - Status field

## Files
- `ecm-frontend/src/components/dialogs/VersionHistoryDialog.tsx`
- `ecm-frontend/e2e/version-share-download.spec.ts`
