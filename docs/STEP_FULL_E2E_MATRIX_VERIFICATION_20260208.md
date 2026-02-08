# Step: Full E2E Matrix Verification (2026-02-08)

## Scope
- Full Playwright E2E suite executed twice against:
  - `ECM_UI_URL=http://localhost:3000` (local latest frontend source)
  - `ECM_UI_URL=http://localhost:5500` (container/deployed frontend endpoint)

## Commands

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 \
  npx playwright test --workers=1

ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 \
  npx playwright test --workers=1
```

## Results

## Round A (`3000`)
- Final result: `39 passed`, `2 failed`, `3 skipped`
- Failing tests:
  - `e2e/ui-smoke.spec.ts` -> `UI smoke: PDF upload + search + version history + preview`
  - `e2e/ui-smoke.spec.ts` -> `UI search download failure shows error toast`
- Notes:
  - Two failures are in UI-smoke download flow assertions (button visibility/request wait), not auth/bootstrap failures.

## Round B (`5500`)
- Final result: `39 passed`, `2 failed`, `3 skipped`
- Failing tests:
  - `e2e/search-preview-status.spec.ts` -> unsupported-preview label assertion (search results)
  - `e2e/search-preview-status.spec.ts` -> unsupported-preview label assertion (advanced search)
- Notes:
  - Matches known difference when `5500` serves an older frontend build without latest unsupported-preview wording/behavior.

## Observations
- Suite health is generally stable across both targets.
- Remaining gaps are concentrated and reproducible:
  - one UI-smoke download path issue (`3000`)
  - one preview-status wording/behavior drift (`5500`)

## Artifacts
- Playwright traces/screenshots/videos generated under:
  - `ecm-frontend/test-results/`
  - `ecm-frontend/playwright-report/`
