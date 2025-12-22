# File Browser View Action - Design

Date: 2025-12-22

## Goal
Make PDF (and other non-office) documents openable from the file browser via a clear "View" action instead of only showing "Edit Online".

## Problem
The file browser context menu only offered "Edit Online"/"View Online" (WOPI) for all documents, which is confusing for PDFs and non-office files. Users had no direct "View" action for previewing PDFs.

## Approach
- Add a "View" menu item for documents in the file browser context menu.
- Restrict WOPI "Edit Online"/"View Online" to office file types only.
- Enable double-click on documents to open the preview dialog.
- Wire the preview dialog at the FileBrowser page level, matching the Search Results behavior.

## Files Updated
- `ecm-frontend/src/components/browser/FileList.tsx`
- `ecm-frontend/src/pages/FileBrowser.tsx`

## Risks / Rollback
- Low risk; limited to file browser UI actions.
- Rollback by removing the preview hook in FileBrowser and restoring the old context menu.
