# Phase367ZL Search Dialog Checkout Prefill And Filters Verification

## Scope

- `SearchDialog` checkout state filter
- `SearchDialog` checkout user filter
- advanced-search URL to dialog checkout prefill
- saved-search template checkout persistence

## Commands

```bash
cd ecm-frontend && ./node_modules/.bin/eslint src/components/search/SearchDialog.tsx src/utils/searchPrefillUtils.ts src/store/slices/uiSlice.ts
cd ecm-frontend && npm run -s build
git diff --check -- \
  ecm-frontend/src/components/search/SearchDialog.tsx \
  ecm-frontend/src/utils/searchPrefillUtils.ts \
  ecm-frontend/src/store/slices/uiSlice.ts \
  docs/PHASE367ZL_SEARCH_DIALOG_CHECKOUT_PREFILL_AND_FILTERS_DEV_20260327.md \
  docs/PHASE367ZL_SEARCH_DIALOG_CHECKOUT_PREFILL_AND_FILTERS_VERIFICATION_20260327.md
```

## Result

- ESLint passed.
- Frontend production build passed.
- Targeted `git diff --check` passed.
