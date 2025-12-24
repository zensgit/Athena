# Preview Layout Verification - 2025-12-24

## Scope
Validate bottom whitespace after the preview height fix for PDF/text/image/WOPI views in the frontend.

## Environment
- UI: http://localhost:5500
- User: admin
- Containers: via `docker compose up -d --build ecm-frontend`

## Results
- PDF preview: no bottom gap
  - File: `J0924032-02上罐体组件v2-模型.pdf`
  - Measurements: windowHeight=820, dialogBottom=820, previewBottom=820, gapWindowToPreview=0
- Text preview: no bottom gap
  - File: `pipeline-version-test.txt`
  - Measurements: windowHeight=720, dialogBottom=720, previewBottom=720, gapWindowToPreview=0
- Image preview: no bottom gap
  - File: `mcp-preview-test.png`
  - Measurements: windowHeight=720, dialogBottom=720, previewBottom=720, gapWindowToPreview=0
- WOPI (XLSX) view: no bottom gap in the preview overlay
  - File: `工作簿1.xlsx` (View → Collabora iframe)
  - Measurements: windowHeight=720, dialogBottom=720, previewBottom=720, gapWindowToPreview=0

## Notes
- The preview container now matches the dialog height (`100vh - 64px`), removing the extra bottom space observed earlier.
- A 1x1 PNG test file (`/tmp/mcp-preview-test.png`) was uploaded for the image preview check.
