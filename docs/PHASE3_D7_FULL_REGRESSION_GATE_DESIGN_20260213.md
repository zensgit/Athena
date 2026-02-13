# Phase 3 Day 7 Design: Full Regression Gate and Release Readiness

Date: 2026-02-13

## Goal

Close Phase 3 with a reproducible regression gate covering:

1. core backend regression
2. UI smoke and search/preview flows
3. OCR queue and preview-status governance

## Gate Definition

### Backend Gate

```bash
cd ecm-core
mvn -q test
```

### Frontend E2E Gate (weekly subset)

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 ECM_E2E_WORKERS=1 \
  npx playwright test --workers=1 \
    e2e/ui-smoke.spec.ts \
    e2e/search-view.spec.ts \
    e2e/search-preview-status.spec.ts \
    e2e/pdf-preview.spec.ts \
    e2e/ocr-queue-ui.spec.ts \
    --project=chromium
```

## Pass Criteria

1. Backend command exits `0`.
2. Playwright subset reaches all tests passed.
3. No blocking regression in:
   - preview status chips/actions
   - PDF preview fallback
   - OCR queue UI actions
   - mail automation entry flow inside UI smoke

## Rollup

After gate pass:

1. update docs index
2. publish execution progress document
3. keep gate commands as default pre-merge checks for this branch

