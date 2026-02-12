# Phase 1 P100 Verification: Preview Governance Parity (Advanced Search)

Date: 2026-02-12

## Validation Commands

1. Unit tests

```bash
cd ecm-frontend
CI=true npm test -- --watch=false src/utils/searchPrefillUtils.test.ts src/utils/savedSearchUtils.test.ts
```

Result:
- PASS (`2 suites`, `11 tests`)

2. Lint

```bash
cd ecm-frontend
npx eslint \
  src/pages/AdvancedSearchPage.tsx \
  src/utils/searchPrefillUtils.ts \
  src/utils/searchPrefillUtils.test.ts \
  e2e/search-dialog-active-criteria-summary.spec.ts \
  e2e/advanced-search-fallback-governance.spec.ts
```

Result:
- PASS

3. Targeted governance test

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 \
  npx playwright test \
  e2e/advanced-search-fallback-governance.spec.ts \
  --grep "hides retry actions when failed previews are all unsupported" \
  --reporter=list
```

Result:
- PASS (`1 passed`)

4. Combined regression pass

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 \
  npx playwright test \
  e2e/saved-search-load-prefill.spec.ts \
  e2e/search-dialog-active-criteria-summary.spec.ts \
  e2e/search-dialog-preview-status.spec.ts \
  e2e/advanced-search-fallback-governance.spec.ts \
  --grep "hides retry actions when failed previews are all unsupported|Saved search load to advanced search prefill|Search dialog active criteria summary|Search dialog preview status filter" \
  --reporter=list
```

Result:
- PASS (`13 passed`)

## New E2E Coverage Added

- `search-dialog-active-criteria-summary.spec.ts`
  - `normalizes legacy preview status aliases from /search URL state`
- `advanced-search-fallback-governance.spec.ts`
  - `hides retry actions when failed previews are all unsupported`

## Outcome

- Advanced Search URL parsing now accepts legacy preview status aliases.
- Unsupported-only failure pages correctly hide retry actions and display explanatory guidance.
