# P5 PR-78 RM Record-Only Search Filter Foundation Verification

## Scope Verified

- backend search filters now support additive `recordOnly` semantics without a new endpoint or migration
- full-text and faceted search builders both reuse the same RM projection helper
- `SearchResults`, `AdvancedSearchPage`, URL state, saved-search mapping, and search-dialog prefill all preserve `recordOnly`
- the slice remains on the existing search/evidence path and does not introduce a second RM surface

## Checks

### Backend targeted tests

```bash
cd ecm-core && ./mvnw -B -Dstyle.color=never test -Dtest=SearchControllerTest,SearchRecordProjectionHelperTest
```

Result:

- `Tests run: 31, Failures: 0, Errors: 0, Skipped: 0`

### Frontend targeted tests

```bash
cd ecm-frontend && CI=true npm test -- --watchAll=false --runInBand --runTestsByPath src/utils/advancedSearchStateUtils.test.ts src/utils/searchPrefillUtils.test.ts src/utils/savedSearchUtils.test.ts src/services/nodeService.recordProjection.test.ts --forceExit
```

Result:

- `Test Suites: 4 passed, 4 total`
- `Tests: 23 passed, 23 total`

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

- helper coverage confirms the RM projection query is built from the full authoritative `rm:*` projection property set
- controller coverage confirms `recordOnly` participates in normalization/filter-count diagnostics
- this slice intentionally stops at query/filter plumbing; no new saved-search authoring UI or dedicated RM search endpoint was added
