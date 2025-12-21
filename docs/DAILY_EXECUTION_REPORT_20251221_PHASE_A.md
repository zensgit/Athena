# Daily Execution Report - Phase A (Preview & Stability)

## Scope
Stabilize preview chain (PDF/WOPI/CAD) and check for major 4xx/5xx signals.

## Validation Results
- **PDF preview**: ✅ E2E test passed (see `docs/DAILY_EXECUTION_REPORT_20251221_PDF_PREVIEW.md`).
- **WOPI health**: ✅ `/api/v1/integration/wopi/health` returns discovery + capabilities OK.
- **WOPI editor URL**: ✅ `/api/v1/integration/wopi/url/{docId}` returned a Collabora URL; `cool.html` responded `200`.
- **ML health**: ✅ `/api/v1/ml/health` returns `200`.

## CAD Preview Status
- **Result**: ❌ Preview failed (backend returns `supported=false`).
- **Reason**: CAD render service returns `422` with message:
  - `DWG conversion unavailable: set ODA_FILE_CONVERTER_EXE or DWG_TO_DXF_CMD`
- **Impact**: CAD preview remains blocked until the CAD render service is configured with a converter.

## Observed Warnings
- **ClamAV**: `Connection refused` in `athena-ecm-core` logs; uploads allowed but virus scan skipped.
  - Action needed: fix ClamAV connectivity or disable scanning for environments without ClamAV.

## Notes
- For API tests, `unified-portal` tokens include realm roles and pass permission checks; `ecm-api` password tokens do not include roles and can yield 403.
