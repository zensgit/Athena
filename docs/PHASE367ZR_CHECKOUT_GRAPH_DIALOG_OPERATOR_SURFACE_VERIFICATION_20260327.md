# Phase367ZR Checkout Graph Dialog Operator Surface Verification

## Scope

- shared checkout-graph dialog
- checkout-graph formatting utility
- `SearchResults` checkout-graph entry point
- `FileList` checkout-graph entry point

## Commands

```bash
cd ecm-frontend && ./node_modules/.bin/eslint \
  src/components/dialogs/CheckoutGraphDialog.tsx \
  src/components/browser/FileList.tsx \
  src/pages/SearchResults.tsx \
  src/utils/checkoutGraphUtils.ts \
  src/utils/checkoutGraphUtils.test.ts
cd ecm-frontend && CI=true npm test -- --watch=false --runInBand src/utils/checkoutGraphUtils.test.ts
cd ecm-frontend && npm run -s build
git diff --check -- \
  ecm-frontend/src/components/dialogs/CheckoutGraphDialog.tsx \
  ecm-frontend/src/components/browser/FileList.tsx \
  ecm-frontend/src/pages/SearchResults.tsx \
  ecm-frontend/src/utils/checkoutGraphUtils.ts \
  ecm-frontend/src/utils/checkoutGraphUtils.test.ts \
  docs/PHASE367ZR_CHECKOUT_GRAPH_DIALOG_OPERATOR_SURFACE_DEV_20260327.md \
  docs/PHASE367ZR_CHECKOUT_GRAPH_DIALOG_OPERATOR_SURFACE_VERIFICATION_20260327.md
```

## Result

- Frontend ESLint passed.
- Focused checkout-graph utility tests passed.
- Frontend production build passed.
- Targeted `git diff --check` passed.
