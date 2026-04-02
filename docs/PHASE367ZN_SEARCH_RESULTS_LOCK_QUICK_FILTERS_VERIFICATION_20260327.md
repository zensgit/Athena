# Phase367ZN Search Results Lock Quick Filters Verification

## Scope

- `SearchResults` lock state filter
- `SearchResults` lock owner filter
- ordinary-search to dialog lock prefill handoff
- fallback criteria key lock-awareness

## Commands

```bash
cd ecm-frontend && ./node_modules/.bin/eslint src/pages/SearchResults.tsx
cd ecm-frontend && npm run -s build
git diff --check -- \
  ecm-frontend/src/pages/SearchResults.tsx \
  docs/PHASE367ZN_SEARCH_RESULTS_LOCK_QUICK_FILTERS_DEV_20260327.md \
  docs/PHASE367ZN_SEARCH_RESULTS_LOCK_QUICK_FILTERS_VERIFICATION_20260327.md
```

## Result

- ESLint passed.
- Frontend production build passed.
- Targeted `git diff --check` passed.
