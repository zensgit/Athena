# PDF Annotate Menu Verification

Date: 2025-12-26

## Changes Under Test
- Added Annotate entry to PDF context menu (requires write permission).
- Added initialAnnotateMode support in DocumentPreview to open annotation mode directly.

## Build
- `docker compose up -d --build ecm-frontend` succeeded (frontend container rebuilt and restarted).

## Runtime Verification
- http://localhost:5500/browse/root (after reload): PDF context menu shows "Annotate" for PDF rows when user has write access.
- Clicking "Annotate" opens the PDF preview with the toolbar button showing "Annotating" (annotation mode enabled immediately).

## Result
- Annotate menu entry works and opens PDF preview directly in annotation mode.
