# Verification: E2E full run (2026-01-11)

## Tests
- `ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test`

## Results
- PASS (19 passed)

## Notes
- Rerun after UploadDialog auto-close fix; previous run failed at `e2e/ui-smoke.spec.ts:756:5`.
- Prior failure artifacts retained at `ecm-frontend/test-results/ui-smoke-UI-smoke-PDF-upload-search-version-history-preview-chromium/`.
