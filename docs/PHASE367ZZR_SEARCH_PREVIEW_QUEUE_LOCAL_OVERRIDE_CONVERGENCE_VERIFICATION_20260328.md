# Phase367ZZR Search Preview Queue Local Override Convergence Verification

## Scope

Verified:

- `ecm-frontend/src/pages/SearchResults.tsx`
- `ecm-frontend/src/pages/AdvancedSearchPage.tsx`

## Commands

```bash
cd ecm-frontend && ./node_modules/.bin/eslint src/pages/SearchResults.tsx src/pages/AdvancedSearchPage.tsx
cd ecm-frontend && npm run -s build
git diff --check -- \
  ecm-frontend/src/pages/SearchResults.tsx \
  ecm-frontend/src/pages/AdvancedSearchPage.tsx \
  docs/PHASE367ZZR_SEARCH_PREVIEW_QUEUE_LOCAL_OVERRIDE_CONVERGENCE_DEV_20260328.md \
  docs/PHASE367ZZR_SEARCH_PREVIEW_QUEUE_LOCAL_OVERRIDE_CONVERGENCE_VERIFICATION_20260328.md
```

## Result

All commands passed.

## Notes

- Ordinary search now recomputes failed preview scopes and status counts from queue-local preview overrides.
- Advanced search now does the same, and batch queue actions persist richer preview fields into local queue state.
- This phase intentionally does not change public API shape again; it finishes the consumer-side convergence for the richer queue response introduced earlier.
