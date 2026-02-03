# Phase 14 E2E Stability (Verification) - 2026-02-02

## Environment
- UI: `http://localhost:5500`
- API: `http://localhost:7700`
- Auth bypass enabled for E2E via `ECM_E2E_SKIP_LOGIN=1` (frontend built with `REACT_APP_E2E_BYPASS_AUTH=1`).

## Playwright E2E
Command:
```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 \
ECM_API_URL=http://localhost:7700 \
ECM_E2E_SKIP_LOGIN=1 \
npx playwright test e2e/pdf-preview.spec.ts e2e/version-share-download.spec.ts
```

Result:
- 5 tests passed
  - PDF preview dialog + controls
  - PDF preview fallback when client PDF fails
  - File browser view action opens preview
  - Version history actions: download + restore
  - Share link enforcement

Notes:
- Targeted suites were re-run after E2E login bypass and check-in retry fixes.
- Full E2E run can be executed with:
  ```bash
  ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 ECM_E2E_SKIP_LOGIN=1 npx playwright test
  ```
