# Phase367ZA Version History Checkout Graph Consumption Verification

## Scope

- `VersionHistoryDialog` graph-first checkout lineage consumption
- Compatibility fallback to old relation/lineage sources

## Commands

```bash
cd ecm-frontend && ./node_modules/.bin/eslint src/components/dialogs/VersionHistoryDialog.tsx src/services/nodeService.ts
cd ecm-frontend && npm run -s build
git diff --check -- \
  ecm-frontend/src/components/dialogs/VersionHistoryDialog.tsx \
  ecm-frontend/src/services/nodeService.ts \
  docs/PHASE367ZA_VERSION_HISTORY_CHECKOUT_GRAPH_CONSUMPTION_DEV_20260327.md \
  docs/PHASE367ZA_VERSION_HISTORY_CHECKOUT_GRAPH_CONSUMPTION_VERIFICATION_20260327.md
```

## Result

- ESLint passed for the touched frontend files.
- Frontend production build passed.
- Targeted `git diff --check` passed.

## Notes

- This phase is additive and compatibility-safe.
- `VersionHistoryDialog` still keeps old relation/lineage fallbacks so the graph endpoint is not a hard dependency yet.
