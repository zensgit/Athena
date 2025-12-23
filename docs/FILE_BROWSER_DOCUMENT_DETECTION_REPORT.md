# File Browser Document Detection Fallback Report

Date: 2025-12-23

## Goal
Ensure file browser actions (View/Download/Version History, etc.) still appear when backend nodes omit or mislabel `nodeType`, by inferring document status from metadata and filename.

## Changes
- Added a document heuristic in the file browser list to decide whether an item is a document.
- Updated FileBrowser preview handlers to use the heuristic instead of strict `nodeType === DOCUMENT` checks.

## Files Updated
- `ecm-frontend/src/components/browser/FileList.tsx`
- `ecm-frontend/src/pages/FileBrowser.tsx`

## Verification
- Command: `npx playwright test e2e/pdf-preview.spec.ts`
- Result: PASS (3 tests, ~10s)

## Notes
- This keeps folder navigation behavior intact while restoring View/Edit/Download actions for documents with incomplete metadata.
