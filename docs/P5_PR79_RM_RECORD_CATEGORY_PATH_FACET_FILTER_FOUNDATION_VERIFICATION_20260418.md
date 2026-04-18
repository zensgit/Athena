# P5 PR-79 RM Record-Category-Path Facet Filter Foundation Verification

## Scope Verified

- backend search filters now accept `recordCategoryPaths`
- default faceted search now exposes `recordCategoryPath` buckets on the existing facet pipeline
- `SearchResults` and `AdvancedSearchPage` now consume the facet and send filter payloads on the existing search path
- saved-search and advanced-search URL state preserve `recordCategoryPaths`
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
cd ecm-frontend && CI=true npm test -- --watchAll=false --runInBand --runTestsByPath src/utils/advancedSearchStateUtils.test.ts src/utils/savedSearchUtils.test.ts src/services/nodeService.recordProjection.test.ts --forceExit
```

Result:

- `Test Suites: 3 passed, 3 total`
- `Tests: 20 passed, 20 total`

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

- this slice intentionally stops at facet/filter plumbing and page-level consumption
- `SearchDialog` remains out of scope because it has no authoritative non-admin RM category option source
- frontend page behavior for the new facet is covered by type-safe build validation plus shared URL/saved-search/payload tests
