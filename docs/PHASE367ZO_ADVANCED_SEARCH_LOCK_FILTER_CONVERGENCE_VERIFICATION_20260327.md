# Phase367ZO Advanced Search Lock Filter Convergence Verification

## Scope

- advanced-search lock URL state
- advanced-search lock template restore
- advanced-search lock request building
- preview batch payload lock propagation

## Commands

```bash
cd ecm-frontend && ./node_modules/.bin/eslint src/pages/AdvancedSearchPage.tsx src/utils/advancedSearchStateUtils.ts src/utils/searchPrefillUtils.ts
cd ecm-frontend && CI=true npm test -- --watch=false --runInBand src/utils/advancedSearchStateUtils.test.ts src/utils/searchPrefillUtils.test.ts
cd ecm-frontend && npm run -s build
git diff --check -- \
  ecm-frontend/src/pages/AdvancedSearchPage.tsx \
  ecm-frontend/src/utils/advancedSearchStateUtils.ts \
  ecm-frontend/src/utils/searchPrefillUtils.ts \
  docs/PHASE367ZO_ADVANCED_SEARCH_LOCK_FILTER_CONVERGENCE_DEV_20260327.md \
  docs/PHASE367ZO_ADVANCED_SEARCH_LOCK_FILTER_CONVERGENCE_VERIFICATION_20260327.md
```

## Result

- Frontend ESLint passed.
- Focused frontend tests passed.
- Frontend production build passed.
- Targeted `git diff --check` passed.
