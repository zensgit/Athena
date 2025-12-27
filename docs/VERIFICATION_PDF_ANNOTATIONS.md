# PDF Annotations Verification Report

Date: 2025-12-25

## Scope
- Validate end-to-end PDF annotation flow after rebuild/restart.
- Confirm annotations persist on reload.
- Confirm annotations are disabled when PDF is rotated.
- Verify PDF fit mode controls, persistence, and keyboard shortcuts.

## Design Summary
- Backend stores PDF annotations under node metadata key `pdfAnnotations` and logs `PDF_ANNOTATIONS_UPDATED`.
- Frontend PDF preview exposes an `Annotate` toggle and renders markers on top of the PDF page.
- Click-to-add opens a dialog, saves via `POST /api/v1/documents/{id}/annotations`, and reload shows markers.
- PDF preview supports fit modes (screen/height/width/actual) with persistent selection and keyboard shortcuts.

## Build/Deploy
- `docker compose build ecm-core ecm-frontend`
- `docker compose up -d ecm-core ecm-frontend`

## Manual Verification (MCP)
1. Login at `http://localhost:5500/` with `admin/admin`.
2. Open a PDF from Root list (e.g., `J0924032-02上罐体组件v2-模型.pdf`).
3. Click `Annotate`, click on the PDF to create a marker.
4. Enter text `Test annotation from MCP` and save.
5. Close preview, reopen the same PDF.

## Fit-to-Screen Verification (MCP)
1. Rebuild and restart frontend: `docker compose build ecm-frontend` and `docker compose up -d ecm-frontend`.
2. Open the same PDF using the `View` action.
3. Confirm toolbar shows `Fit to screen (59%)`.
4. Measure canvas bounds via console: `canvasRect.bottom === contentRect.bottom`.
5. Click zoom in (scale shows `84%`), then click `Fit to screen (59%)` to restore auto-fit.

## Fit Mode + Shortcut Verification (MCP)
1. Create folder `pdf-fit-test-1766640031`.
2. Upload PDFs:
   - `改图28.pdf`
   - `3-291-249-885-00_RevB1_mx.pdf`
   - `3-752-062-962-00_RevB2_mx.pdf`
   - `3-752-063-959-00_RevC1_mx.pdf`
   - `1-490-031-395-00_RevD.PDF`
   - `简易三坐标数控机床设计.pdf` (uploaded via API; UI showed `An unexpected error occurred` toast)
3. Open `简易三坐标数控机床设计.pdf` and use the fit menu:
   - Select `Fit to screen` → toolbar shows `Fit to screen (83%)`.
   - Press `0` → toolbar shows `Actual size (100%)`.
   - Press `+` → zoom shows `125%`.
   - Press `F` → zoom resets to `100%`.
4. Open `1-490-031-395-00_RevD.PDF` and confirm persisted fit mode `Actual size (100%)`.
5. Measure canvas bounds on both PDFs: bottom gap `0px`.

## Upload Toast Verification (MCP)
1. Upload `/tmp/upload-toast-test-1766641807.txt` from the same folder.
2. Confirm only the success toast appears and no `An unexpected error occurred` toast.
3. Verify the new file shows in the list with size `29.00 B`.

## Results
- Annotation dialog opened and saved successfully.
- Header shows `Notes updated` timestamp.
- Annotation text was visible after reopening, confirming persistence.
- Rotating the PDF disables the `Annotate` action until rotation returns to `0°`.
- PDF auto-fit reduced bottom whitespace: measured bottom gap `0px` with `Fit to screen (59%)`.
- Manual zoom exits auto-fit; clicking `Fit to screen` restores the fitted scale and bottom gap stays `0px`.
- Fit mode menu shows all options and persists across document previews.
- Keyboard shortcuts `F`, `0`, `+`, `-` apply fit/zoom as expected.
- Upload dialog now reports partial failure counts correctly and no longer shows conflicting success + error toasts for a successful upload.

## Notes
- PDF preview remains read-only; annotations are separate metadata.
- UI upload dialog intermittently showed `An unexpected error occurred` despite successful uploads; the large 31.24 MB PDF was uploaded via API and verified in the list.
