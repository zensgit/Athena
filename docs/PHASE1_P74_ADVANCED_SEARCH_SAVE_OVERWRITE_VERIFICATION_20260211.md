# Phase 1 (P74) - Verification: Advanced Search Save Overwrite

Date: 2026-02-11

## What Was Verified
1. Advanced Search save dialog supports `Create new` and `Update existing`.
2. Updating an existing saved search from the dialog persists the new query params.
3. Existing P73 behaviors remain stable:
   - rename / duplicate on Saved Searches page
   - deep link execute from `savedSearchId`

## Automated Verification (Playwright)
### New test
- `ecm-frontend/e2e/saved-search-overwrite-from-dialog.spec.ts`

### P74 scenario
1. API ready check and login token retrieval.
2. Upload two text documents (`docA`, `docB`) and wait for indexing.
3. Create saved search via API with query=`docA`.
4. Open Advanced Search dialog from `/browse/root`, set query to `docB`.
5. Open `Save Search` dialog:
   - switch mode to `Update existing`
   - select created saved search
   - click `Update`
6. Navigate to `/search-results?savedSearchId=<id>`.
7. Assert updated result contains `docB`.

### Regression tests run
- `ecm-frontend/e2e/saved-search-crud-update.spec.ts`

### Commands executed
```bash
./scripts/rebuild-frontend-prebuilt.sh

cd ecm-frontend
npx playwright test e2e/saved-search-overwrite-from-dialog.spec.ts e2e/saved-search-crud-update.spec.ts
npx playwright test
```

## Results
- Targeted tests: passed.
- Full frontend E2E: `53 passed, 4 skipped`.

## Notes
- Frontend prebuilt rebuild is required for `http://localhost:5500` validation.
- No credentials or tokens are stored in repository files.

