# Phase367N Advanced Search Result Lock Visibility Verification

## Verified Behavior

- advanced-search result mapping now preserves `locked` and `lockedBy`
- locked documents render a lock chip in result cards
- owner-aware chip text is shown when `lockedBy` is present
- unlocked documents do not render the chip
- checkout and advanced-search state tests continue to pass alongside the new lock chip helper

## Commands

```bash
cd ecm-frontend && ./node_modules/.bin/eslint src/pages/AdvancedSearchPage.tsx src/utils/advancedSearchLockUtils.ts src/utils/advancedSearchLockUtils.test.ts
cd ecm-frontend && CI=true npm test -- --watch=false --runInBand src/utils/advancedSearchLockUtils.test.ts src/utils/advancedSearchCheckoutUtils.test.ts src/utils/advancedSearchStateUtils.test.ts
cd ecm-frontend && npm run -s build
git diff --check -- ecm-frontend/src/pages/AdvancedSearchPage.tsx ecm-frontend/src/utils/advancedSearchLockUtils.ts ecm-frontend/src/utils/advancedSearchLockUtils.test.ts docs/PHASE367N_ADVANCED_SEARCH_RESULT_LOCK_VISIBILITY_DEV_20260327.md docs/PHASE367N_ADVANCED_SEARCH_RESULT_LOCK_VISIBILITY_VERIFICATION_20260327.md
```

## Result

- ESLint passed
- Jest passed
- production build passed
- targeted `git diff --check` passed
