# Phase368ZH Advanced Search Queue Mutation Contract Convergence Verification

## Commands

```bash
cd ecm-frontend && ./node_modules/.bin/eslint src/pages/AdvancedSearchPage.tsx src/utils/previewQueueOverrideUtils.ts src/utils/previewQueueOverrideUtils.test.ts
cd ecm-frontend && CI=true npm test -- --watch=false --runInBand src/utils/previewQueueOverrideUtils.test.ts
cd ecm-frontend && npm run -s build
git diff --check -- ecm-frontend/src/pages/AdvancedSearchPage.tsx docs/PHASE368ZH_ADVANCED_SEARCH_QUEUE_MUTATION_CONTRACT_CONVERGENCE_DEV_20260401.md docs/PHASE368ZH_ADVANCED_SEARCH_QUEUE_MUTATION_CONTRACT_CONVERGENCE_VERIFICATION_20260401.md
```

## Result

- `AdvancedSearchPage.tsx` compiles with the shared queue override helper
- shared helper tests pass
- frontend build completes successfully with only the repo's existing warnings
