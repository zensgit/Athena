# Phase367ZP Saved Search Lock Checkout Replay Verification

## Scope

- `savedSearchUtils` lock/checkout replay
- saved-search advanced-state restore
- saved-search execution result typing for lock/checkout node fields

## Commands

```bash
cd ecm-frontend && ./node_modules/.bin/eslint src/utils/savedSearchUtils.ts src/utils/savedSearchUtils.test.ts src/services/savedSearchService.ts src/store/slices/nodeSlice.ts
cd ecm-frontend && CI=true npm test -- --watch=false --runInBand src/utils/savedSearchUtils.test.ts
cd ecm-frontend && npm run -s build
git diff --check -- \
  ecm-frontend/src/utils/savedSearchUtils.ts \
  ecm-frontend/src/utils/savedSearchUtils.test.ts \
  ecm-frontend/src/services/savedSearchService.ts \
  ecm-frontend/src/store/slices/nodeSlice.ts \
  docs/PHASE367ZP_SAVED_SEARCH_LOCK_CHECKOUT_REPLAY_DEV_20260327.md \
  docs/PHASE367ZP_SAVED_SEARCH_LOCK_CHECKOUT_REPLAY_VERIFICATION_20260327.md
```

## Result

- Frontend ESLint passed.
- Focused saved-search tests passed.
- Frontend production build passed.
- Targeted `git diff --check` passed.
