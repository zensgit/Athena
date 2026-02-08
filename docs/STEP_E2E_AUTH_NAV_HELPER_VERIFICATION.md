# Step: E2E Auth Navigation Helper (Verification)

## Validation Commands

1. Combined Playwright regression on local latest frontend build
```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 \
  npx playwright test e2e/search-preview-status.spec.ts e2e/mail-automation.spec.ts --workers=1
```

## Result
- `9 passed`
- `3 skipped` (data-dependent skips in mail diagnostics/replay/runtime panels)
- `0 failed`

## Notes
- Same command against `ECM_UI_URL=http://localhost:5500` can fail for unsupported-preview assertions when that target serves an older frontend build.
- This step validates code behavior against the latest local source build (`3000`), which includes the new preview-status UI logic.
