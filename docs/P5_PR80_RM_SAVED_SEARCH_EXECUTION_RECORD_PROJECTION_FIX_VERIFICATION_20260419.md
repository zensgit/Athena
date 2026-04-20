# P5 PR80 RM Saved Search Execution Record Projection Fix Verification

## Scope Verified

- saved-search execution no longer drops RM projection fields on the frontend
- `executeSavedSearch(...)` now preserves:
  - `record`
  - `declared*`
  - `recordCategory*`
  - `rm:record` aspect
- the saved-search prefill chain remains intact

## Commands

### Frontend targeted tests

```bash
cd ecm-frontend && CI=true npm test -- --watchAll=false --runInBand --runTestsByPath \
  src/store/slices/nodeSlice.test.ts \
  src/pages/SavedSearchesPage.test.tsx \
  src/utils/searchPrefillUtils.test.ts \
  --forceExit
```

Result:

- `Test Suites: 3 passed, 3 total`
- `Tests: 9 passed, 9 total`

### Frontend build

```bash
cd ecm-frontend && npm run build
```

Result:

- passed
- remaining warnings are pre-existing:
  - `ShareLinkManager.tsx`
  - `AdminDashboard.tsx`

### Static diff check

```bash
git diff --check
```

Result:

- passed

## Coverage Notes

The targeted regression coverage now includes:

- slice-level saved-search execution mapping for RM projection
- saved-search load-to-search prefill retention for `recordOnly` and `recordCategoryPaths`
- URL/search-prefill parsing for `recordCategoryPaths`

## Residual Manual Check

Still recommended in staging:

- run a saved search that returns a declared record
- open `/search-results?savedSearchId=...`
- confirm `RecordStatusChip` and record category path match normal search / advanced search behavior
