# P5 PR-79 RM Record-Category-Path Facet Filter Foundation Verification

## Scope Verified

- backend search filters now accept `recordCategoryPaths`
- default faceted search now exposes `recordCategoryPath` buckets on the existing facet pipeline
- `SearchResults`, `AdvancedSearchPage`, and `SearchDialog` now consume or preserve the filter on the existing search path
- saved-search, advanced-search URL state, and `SavedSearchesPage -> Load to search` preserve `recordCategoryPaths`
- no new backend endpoint or migration was added

## Checks

### Backend targeted test

```bash
cd ecm-core && ./mvnw -B -Dstyle.color=never test -Dtest=SearchControllerTest
```

Result:

- `Tests run: 29, Failures: 0, Errors: 0, Skipped: 0`

### Frontend targeted tests

```bash
cd ecm-frontend && CI=true npm test -- --watchAll=false --runInBand --runTestsByPath src/utils/advancedSearchStateUtils.test.ts src/utils/savedSearchUtils.test.ts src/services/nodeService.recordProjection.test.ts src/utils/searchPrefillUtils.test.ts src/pages/SavedSearchesPage.test.tsx --forceExit
```

Result:

- `Test Suites: 5 passed, 5 total`
- `Tests: 26 passed, 26 total`

### Frontend production build

```bash
cd ecm-frontend && npm run build
```

Result:

- passed
- existing repo warnings remain:
  - `src/components/share/ShareLinkManager.tsx`
  - `src/pages/AdminDashboard.tsx`

### Static diff check

```bash
git diff --check
```

Result:

- passed

## Notes

- this slice still stops at facet/filter plumbing and existing search-surface consumption
- `SearchDialog` reuses the existing `/search/facets` pipeline; no separate RM picker endpoint was introduced
- frontend behavior for the new facet/filter is covered by shared URL/prefill/saved-search/payload tests plus production build validation
