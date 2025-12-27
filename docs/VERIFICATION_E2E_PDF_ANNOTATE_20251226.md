# E2E PDF Annotate Assertion

Date: 2025-12-26

## Change
- Added assertion in `ecm-frontend/e2e/pdf-preview.spec.ts` to verify the Annotate button is visible for admin when previewing a PDF.

## Test Status
- Not executed here (requires running Playwright with API/UI stack running).

## Suggested Command
```
cd /Users/huazhou/Downloads/Github/Athena/ecm-frontend
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 \
  npx playwright test e2e/pdf-preview.spec.ts
```
