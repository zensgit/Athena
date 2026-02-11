# Phase 1 P83 - Search Dialog Preview Status Filter Parity (Verification) - 2026-02-11

## Verification Scope
- Search dialog supports selecting preview status and treats it as valid search criteria.
- Modal-triggered search sends preview status to API.
- Saved search created from dialog persists preview status filters.
- Saved-search utility mapping restores preview status into `SearchCriteria`.

## Commands
```bash
cd ecm-frontend
npx eslint e2e/helpers/login.ts e2e/saved-search-overwrite-from-dialog.spec.ts e2e/search-dialog-preview-status.spec.ts src/components/search/SearchDialog.tsx src/utils/savedSearchUtils.ts src/utils/savedSearchUtils.test.ts
npm test -- --watch=false --runInBand src/utils/savedSearchUtils.test.ts
ECM_UI_URL=http://localhost:3000 npx playwright test e2e/search-dialog-preview-status.spec.ts e2e/saved-search-overwrite-from-dialog.spec.ts --reporter=list
ECM_UI_URL=http://localhost:3000 npx playwright test e2e/search-fallback-governance.spec.ts e2e/advanced-search-fallback-governance.spec.ts e2e/search-fallback-criteria.spec.ts e2e/saved-search-overwrite-from-dialog.spec.ts e2e/search-dialog-preview-status.spec.ts --reporter=list
```

## Results
- `eslint`: passed.
- `savedSearchUtils` unit test: passed (`1 passed`).
- Targeted P83 e2e set: passed (`2 passed`).
- Consolidated search/fallback regression gate: passed (`5 passed`).

## Conclusion
- P83 is verified complete.
- Search dialog now supports preview-status filtering with end-to-end parity:
  - selection and execution propagation (`previewStatus` query),
  - saved-search persistence,
  - saved-search-to-criteria restoration.
- Regression suite confirms no fallback governance regressions from the dialog enhancement.
