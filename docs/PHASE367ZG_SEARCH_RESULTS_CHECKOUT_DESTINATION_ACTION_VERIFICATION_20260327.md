# Phase367ZG Search Results Checkout Destination Action Verification

## Scope

- `SearchResults` checkout destination action

## Commands

```bash
cd ecm-frontend && ./node_modules/.bin/eslint src/pages/SearchResults.tsx
cd ecm-frontend && npm run -s build
git diff --check -- \
  ecm-frontend/src/pages/SearchResults.tsx \
  docs/PHASE367ZG_SEARCH_RESULTS_CHECKOUT_DESTINATION_ACTION_DEV_20260327.md \
  docs/PHASE367ZG_SEARCH_RESULTS_CHECKOUT_DESTINATION_ACTION_VERIFICATION_20260327.md
```

## Result

- ESLint passed.
- Frontend production build passed.
- Targeted `git diff --check` passed.
