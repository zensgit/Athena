# Phase367ZK Search Results Checkout Quick Filters Verification

## Scope

- `SearchResults` checkout state filter
- `SearchResults` checkout user filter
- ordinary-search to advanced-search checkout prefill

## Commands

```bash
cd ecm-frontend && ./node_modules/.bin/eslint src/pages/SearchResults.tsx src/store/slices/uiSlice.ts
cd ecm-frontend && npm run -s build
git diff --check -- \
  ecm-frontend/src/pages/SearchResults.tsx \
  ecm-frontend/src/store/slices/uiSlice.ts \
  docs/PHASE367ZK_SEARCH_RESULTS_CHECKOUT_QUICK_FILTERS_DEV_20260327.md \
  docs/PHASE367ZK_SEARCH_RESULTS_CHECKOUT_QUICK_FILTERS_VERIFICATION_20260327.md
```

## Result

- ESLint passed.
- Frontend production build passed.
- Targeted `git diff --check` passed.
