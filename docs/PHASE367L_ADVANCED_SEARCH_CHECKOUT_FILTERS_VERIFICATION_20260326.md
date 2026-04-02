# Phase367L Advanced Search Checkout Filters Verification

## Verified Behavior

- backend search filters accept `checkedOut` and `checkoutUser`
- unified advanced search criteria now serialize and restore checkout filters from URL/template state
- `AdvancedSearchPage` exposes checkout-state and checkout-user controls
- preview-governance actions that reuse the current search scope now inherit checkout filters
- backend `SearchControllerTest` still passes with the expanded filter contract

## Commands

```bash
cd ecm-core && mvn -q -Dtest=SearchControllerTest test
cd ecm-frontend && ./node_modules/.bin/eslint src/pages/AdvancedSearchPage.tsx src/services/nodeService.ts src/utils/advancedSearchStateUtils.ts src/utils/advancedSearchStateUtils.test.ts
cd ecm-frontend && CI=true npm test -- --watch=false --runInBand src/utils/advancedSearchStateUtils.test.ts
cd ecm-frontend && npm run -s build
git diff --check -- ecm-core/src/main/java/com/ecm/core/search/SearchFilters.java ecm-core/src/main/java/com/ecm/core/search/FullTextSearchService.java ecm-core/src/main/java/com/ecm/core/controller/SearchController.java ecm-frontend/src/types/index.ts ecm-frontend/src/services/nodeService.ts ecm-frontend/src/utils/advancedSearchStateUtils.ts ecm-frontend/src/utils/advancedSearchStateUtils.test.ts ecm-frontend/src/pages/AdvancedSearchPage.tsx docs/PHASE367L_ADVANCED_SEARCH_CHECKOUT_FILTERS_DEV_20260326.md docs/PHASE367L_ADVANCED_SEARCH_CHECKOUT_FILTERS_VERIFICATION_20260326.md
```

## Result

- backend tests passed
- frontend ESLint passed
- frontend Jest passed
- frontend build passed
- targeted `git diff --check` passed
