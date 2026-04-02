# Phase367ZT Checkout Graph Explicit Node Set Verification

## Scope

- backend checkout-graph DTO extension
- checkout-graph controller regression
- frontend checkout-graph type mapping
- shared dialog consumption of explicit graph nodes

## Commands

```bash
cd ecm-core && mvn -q -Dtest=NodeControllerRelationsTest test
cd ecm-frontend && ./node_modules/.bin/eslint \
  src/services/nodeService.ts \
  src/components/dialogs/CheckoutGraphDialog.tsx \
  src/utils/checkoutGraphUtils.ts \
  src/utils/checkoutGraphUtils.test.ts
cd ecm-frontend && CI=true npm test -- --watch=false --runInBand src/utils/checkoutGraphUtils.test.ts
cd ecm-frontend && npm run -s build
git diff --check -- \
  ecm-core/src/main/java/com/ecm/core/controller/NodeController.java \
  ecm-core/src/test/java/com/ecm/core/controller/NodeControllerRelationsTest.java \
  ecm-frontend/src/services/nodeService.ts \
  ecm-frontend/src/components/dialogs/CheckoutGraphDialog.tsx \
  ecm-frontend/src/utils/checkoutGraphUtils.ts \
  ecm-frontend/src/utils/checkoutGraphUtils.test.ts \
  docs/PHASE367ZT_CHECKOUT_GRAPH_EXPLICIT_NODE_SET_DEV_20260328.md \
  docs/PHASE367ZT_CHECKOUT_GRAPH_EXPLICIT_NODE_SET_VERIFICATION_20260328.md
```

## Result

- Backend controller regression passed.
- Frontend ESLint passed.
- Focused checkout-graph utility tests passed.
- Frontend production build passed.
- Targeted `git diff --check` passed.
