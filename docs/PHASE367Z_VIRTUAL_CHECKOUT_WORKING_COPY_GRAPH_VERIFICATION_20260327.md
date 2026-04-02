# Phase367Z Virtual Checkout Working-Copy Graph Verification

## Scope

- Backend virtual checkout graph endpoint
- Controller regression for graph payload
- Frontend service mapping
- Advanced search relations details consumption

## Commands

```bash
cd ecm-core && mvn -q -Dtest=NodeControllerRelationsTest test
cd ecm-frontend && ./node_modules/.bin/eslint src/pages/AdvancedSearchPage.tsx src/services/nodeService.ts
cd ecm-frontend && npm run -s build
git diff --check -- \
  ecm-core/src/main/java/com/ecm/core/controller/NodeController.java \
  ecm-core/src/test/java/com/ecm/core/controller/NodeControllerRelationsTest.java \
  ecm-frontend/src/services/nodeService.ts \
  ecm-frontend/src/pages/AdvancedSearchPage.tsx \
  docs/PHASE367Z_VIRTUAL_CHECKOUT_WORKING_COPY_GRAPH_DEV_20260327.md \
  docs/PHASE367Z_VIRTUAL_CHECKOUT_WORKING_COPY_GRAPH_VERIFICATION_20260327.md
```

## Result

- `NodeControllerRelationsTest` passed.
- ESLint passed for the touched frontend files.
- Production frontend build passed.
- Targeted `git diff --check` passed.

## Notes

- This phase intentionally does not introduce a persisted working-copy entity.
- Existing `relations/checkout` and `documents/{id}/checkout-lineage` APIs remain unchanged.
- The new graph endpoint is additive and can be promoted later into a fuller checkout/source graph contract.
