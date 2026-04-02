# Phase367ZM Search Lock Filters And Dialog Triage Verification

## Scope

- shared search `locked / lockedBy` filters
- `SearchController` filter diagnostics and copy helpers
- `SearchDialog` lock state and lock owner controls

## Commands

```bash
cd ecm-core && mvn -q -Dtest=SearchControllerTest test
cd ecm-frontend && ./node_modules/.bin/eslint src/components/search/SearchDialog.tsx src/services/nodeService.ts src/store/slices/uiSlice.ts src/types/index.ts src/utils/searchPrefillUtils.ts
cd ecm-frontend && npm run -s build
git diff --check -- \
  ecm-core/src/main/java/com/ecm/core/search/SearchFilters.java \
  ecm-core/src/main/java/com/ecm/core/search/FullTextSearchService.java \
  ecm-core/src/main/java/com/ecm/core/search/FacetedSearchService.java \
  ecm-core/src/main/java/com/ecm/core/controller/SearchController.java \
  ecm-frontend/src/components/search/SearchDialog.tsx \
  ecm-frontend/src/services/nodeService.ts \
  ecm-frontend/src/store/slices/uiSlice.ts \
  ecm-frontend/src/types/index.ts \
  ecm-frontend/src/utils/searchPrefillUtils.ts \
  docs/PHASE367ZM_SEARCH_LOCK_FILTERS_AND_DIALOG_TRIAGE_DEV_20260327.md \
  docs/PHASE367ZM_SEARCH_LOCK_FILTERS_AND_DIALOG_TRIAGE_VERIFICATION_20260327.md
```

## Result

- Backend targeted search tests passed.
- Frontend ESLint passed.
- Frontend production build passed.
- Targeted `git diff --check` passed.
