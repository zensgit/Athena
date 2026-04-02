# Phase367ZS Checkout Graph Shared Surface Convergence Verification

## Scope

- `VersionHistoryDialog` checkout-graph dialog entry
- `DocumentPreview` checkout-graph dialog entry
- `AdvancedSearchPage` checkout-graph dialog entry
- shared checkout-graph summary reuse

## Commands

```bash
cd ecm-frontend && ./node_modules/.bin/eslint \
  src/components/dialogs/VersionHistoryDialog.tsx \
  src/components/preview/DocumentPreview.tsx \
  src/pages/AdvancedSearchPage.tsx \
  src/components/dialogs/CheckoutGraphDialog.tsx \
  src/utils/checkoutGraphUtils.ts
cd ecm-frontend && CI=true npm test -- --watch=false --runInBand src/utils/checkoutGraphUtils.test.ts
cd ecm-frontend && npm run -s build
git diff --check -- \
  ecm-frontend/src/components/dialogs/VersionHistoryDialog.tsx \
  ecm-frontend/src/components/preview/DocumentPreview.tsx \
  ecm-frontend/src/pages/AdvancedSearchPage.tsx \
  docs/PHASE367ZS_CHECKOUT_GRAPH_SHARED_SURFACE_CONVERGENCE_DEV_20260328.md \
  docs/PHASE367ZS_CHECKOUT_GRAPH_SHARED_SURFACE_CONVERGENCE_VERIFICATION_20260328.md
```

## Result

- Frontend ESLint passed.
- Focused checkout-graph utility tests passed.
- Frontend production build passed.
- Targeted `git diff --check` passed.
