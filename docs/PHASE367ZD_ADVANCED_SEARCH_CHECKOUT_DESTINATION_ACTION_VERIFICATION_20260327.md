# Phase367ZD Advanced Search Checkout Destination Action Verification

## Scope

- `AdvancedSearchPage` checkout destination action

## Commands

```bash
cd ecm-frontend && ./node_modules/.bin/eslint src/pages/AdvancedSearchPage.tsx
cd ecm-frontend && npm run -s build
git diff --check -- \
  ecm-frontend/src/pages/AdvancedSearchPage.tsx \
  docs/PHASE367ZD_ADVANCED_SEARCH_CHECKOUT_DESTINATION_ACTION_DEV_20260327.md \
  docs/PHASE367ZD_ADVANCED_SEARCH_CHECKOUT_DESTINATION_ACTION_VERIFICATION_20260327.md
```

## Result

- ESLint passed.
- Frontend production build passed.
- Targeted `git diff --check` passed.
