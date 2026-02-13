# Phase 3 Day 7 Verification: Full Regression Gate

Date: 2026-02-13

## Scope

- Final regression verification for Phase 3 closeout.

## Backend

Command:

```bash
cd ecm-core
mvn -q test
```

Result:

- **Passed (exit code 0)**

## Frontend E2E (weekly subset)

Command:

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

Result:

- **Passed: 22/22**

## Coverage Notes

The successful E2E subset includes:

1. full UI smoke workflow (browse/upload/search/rules/mail/security/antivirus)
2. search list visibility and permissions behavior
3. preview-status filtering and retry-action policy
4. PDF preview and fallback renderer path
5. OCR queue actions from preview UI

## Conclusion

Phase 3 Day 7 regression gate passed. Current branch state is suitable for the planned Phase 3 closeout handoff.

