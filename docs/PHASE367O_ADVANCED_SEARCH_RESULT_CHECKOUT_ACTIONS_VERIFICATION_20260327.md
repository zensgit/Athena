# Phase367O Advanced Search Result Checkout Actions Verification

## Verified Behavior

- advanced-search result rows now expose `Check Out` for eligible document results
- `Check Out` is disabled when a foreign lock blocks checkout
- checked-out results now expose `Cancel Checkout`
- `Cancel Checkout` is disabled for foreign checkout unless the current user is admin
- successful result-row actions refresh the current search page

## Commands

```bash
cd ecm-frontend && ./node_modules/.bin/eslint src/pages/AdvancedSearchPage.tsx src/utils/advancedSearchActionUtils.ts src/utils/advancedSearchActionUtils.test.ts
cd ecm-frontend && CI=true npm test -- --watch=false --runInBand src/utils/advancedSearchActionUtils.test.ts src/utils/advancedSearchLockUtils.test.ts src/utils/advancedSearchCheckoutUtils.test.ts
cd ecm-frontend && npm run -s build
git diff --check -- ecm-frontend/src/pages/AdvancedSearchPage.tsx ecm-frontend/src/utils/advancedSearchActionUtils.ts ecm-frontend/src/utils/advancedSearchActionUtils.test.ts docs/PHASE367O_ADVANCED_SEARCH_RESULT_CHECKOUT_ACTIONS_DEV_20260327.md docs/PHASE367O_ADVANCED_SEARCH_RESULT_CHECKOUT_ACTIONS_VERIFICATION_20260327.md
```

## Result

- ESLint passed
- Jest passed
- production build passed
- targeted `git diff --check` passed
