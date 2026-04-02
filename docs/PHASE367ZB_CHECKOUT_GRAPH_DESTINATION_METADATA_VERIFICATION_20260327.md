# Phase367ZB Checkout Graph Destination Metadata Verification

## Scope

- Backend `checkout-graph` destination metadata
- `VersionHistoryDialog` destination banner consumption

## Commands

```bash
cd ecm-core && mvn -q -Dtest=NodeControllerRelationsTest test
cd ecm-frontend && ./node_modules/.bin/eslint src/components/dialogs/VersionHistoryDialog.tsx src/services/nodeService.ts
cd ecm-frontend && npm run -s build
git diff --check -- \
  ecm-core/src/main/java/com/ecm/core/controller/NodeController.java \
  ecm-core/src/test/java/com/ecm/core/controller/NodeControllerRelationsTest.java \
  ecm-frontend/src/services/nodeService.ts \
  ecm-frontend/src/components/dialogs/VersionHistoryDialog.tsx \
  docs/PHASE367ZB_CHECKOUT_GRAPH_DESTINATION_METADATA_DEV_20260327.md \
  docs/PHASE367ZB_CHECKOUT_GRAPH_DESTINATION_METADATA_VERIFICATION_20260327.md
```

## Result

- `NodeControllerRelationsTest` passed.
- ESLint passed for the touched frontend files.
- Frontend production build passed.
- Targeted `git diff --check` passed.

## Notes

- Destination metadata is still virtual and derived from the document's current parent folder.
- This phase intentionally avoids persistent destination bookkeeping so it can land safely in the current dirty worktree.
