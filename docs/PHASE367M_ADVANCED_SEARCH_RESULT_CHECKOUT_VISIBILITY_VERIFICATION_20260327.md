# Phase367M Advanced Search Result Checkout Visibility Verification

## Verified Behavior

- advanced-search result mapping now preserves `checkedOut` and `checkoutUser`
- checked-out documents render a checkout chip in result cards
- owner-aware chip text is shown when `checkoutUser` is present
- non-checked-out documents do not render the chip
- advanced-search state tests continue to pass after the earlier checkout filter additions

## Commands

```bash
cd ecm-frontend && ./node_modules/.bin/eslint src/pages/AdvancedSearchPage.tsx src/utils/advancedSearchCheckoutUtils.ts src/utils/advancedSearchCheckoutUtils.test.ts
cd ecm-frontend && CI=true npm test -- --watch=false --runInBand src/utils/advancedSearchCheckoutUtils.test.ts src/utils/advancedSearchStateUtils.test.ts
cd ecm-frontend && npm run -s build
git diff --check -- ecm-frontend/src/pages/AdvancedSearchPage.tsx ecm-frontend/src/utils/advancedSearchCheckoutUtils.ts ecm-frontend/src/utils/advancedSearchCheckoutUtils.test.ts docs/PHASE367M_ADVANCED_SEARCH_RESULT_CHECKOUT_VISIBILITY_DEV_20260327.md docs/PHASE367M_ADVANCED_SEARCH_RESULT_CHECKOUT_VISIBILITY_VERIFICATION_20260327.md
```

## Result

- ESLint passed
- Jest passed
- production build passed
- targeted `git diff --check` passed
