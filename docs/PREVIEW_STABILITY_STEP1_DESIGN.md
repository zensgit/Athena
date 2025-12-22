# Preview Stability Step 1 Design: PDF Fallback to Server Preview

## Goal
Reduce PDF preview failures in the UI by falling back to the server-rendered preview API when the client PDF renderer fails.

## Changes
- Frontend `DocumentPreview`:
  - Add a fallback path for PDFs that calls `/api/v1/documents/{id}/preview` when `react-pdf` fails to load.
  - Render server preview pages as images and reuse existing zoom/rotate controls.
  - Surface a clear error message if the server preview is unavailable.
- Frontend `PdfPreview`:
  - Expose `onLoadError` callback to trigger fallback.
- E2E test `pdf-preview.spec.ts`:
  - Accept either client PDF canvas or server fallback rendering.
- API smoke check:
  - Add a preview API verification for the uploaded PDF in `scripts/smoke.sh`.

## Success Criteria
- PDF preview uses client renderer by default.
- If client renderer fails, server preview images render without breaking the dialog.
- Smoke tests verify preview API returns pages.

## Risks / Mitigations
- **Large PDFs**: server preview may be heavier; fallback only triggers on failure.
- **Missing preview support**: surface server message and keep download available.

## Rollback
- Revert fallback logic in `DocumentPreview` and the smoke preview check.
