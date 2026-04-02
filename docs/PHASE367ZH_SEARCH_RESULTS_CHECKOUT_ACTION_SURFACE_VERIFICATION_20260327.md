# Phase367ZH Search Results Checkout Action Surface Verification

## Scope

- `SearchResults` checkout chip
- `SearchResults` checkout/checkin/cancel-checkout actions
- `SearchResults` check-in dialog

## Commands

```bash
cd ecm-frontend && ./node_modules/.bin/eslint src/pages/SearchResults.tsx
cd ecm-frontend && npm run -s build
git diff --check -- \
  ecm-frontend/src/pages/SearchResults.tsx \
  docs/PHASE367ZH_SEARCH_RESULTS_CHECKOUT_ACTION_SURFACE_DEV_20260327.md \
  docs/PHASE367ZH_SEARCH_RESULTS_CHECKOUT_ACTION_SURFACE_VERIFICATION_20260327.md
```

## Result

- ESLint passed.
- Frontend production build passed.
- Targeted `git diff --check` passed.
