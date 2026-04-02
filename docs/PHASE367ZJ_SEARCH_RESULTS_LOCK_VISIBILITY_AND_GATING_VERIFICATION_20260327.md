# Phase367ZJ Search Results Lock Visibility And Gating Verification

## Scope

- `SearchResults` lock chip
- `SearchResults` lock-aware annotate gating
- `SearchResults` lock-aware online-edit gating

## Commands

```bash
cd ecm-frontend && ./node_modules/.bin/eslint src/pages/SearchResults.tsx
cd ecm-frontend && npm run -s build
git diff --check -- \
  ecm-frontend/src/pages/SearchResults.tsx \
  docs/PHASE367ZJ_SEARCH_RESULTS_LOCK_VISIBILITY_AND_GATING_DEV_20260327.md \
  docs/PHASE367ZJ_SEARCH_RESULTS_LOCK_VISIBILITY_AND_GATING_VERIFICATION_20260327.md
```

## Result

- ESLint passed.
- Frontend production build passed.
- Targeted `git diff --check` passed.
