# UI Verification - PDF View/Annotate (2025-12-28)

## Scope
- Confirm PDF context menu options.
- Confirm viewer opens in read-only mode and annotation mode is available.

## Environment
- Frontend: http://localhost:5500
- Login: admin/admin (Keycloak)

## Steps
1. Login to Athena ECM UI.
2. Open the actions menu for a PDF in the root list (filename starting with `J0924032-02`).
3. Verify available menu items.
4. Click `View` to open the PDF preview.
5. Click `Annotate` in the viewer toolbar.
6. Close the viewer.

## Results
- Context menu shows `View` and `Annotate` for PDF items (no `Edit Online` option).
- Viewer opens with banner text: `PDF preview is read-only, annotations available`.
- `Annotate` toggles to `Annotating` and shows `Exit annotate mode`.
- Viewer closes cleanly and returns to list view.

## Notes
- If `Edit Online` is expected for PDFs, it is not present; current flow is view + annotate only.
