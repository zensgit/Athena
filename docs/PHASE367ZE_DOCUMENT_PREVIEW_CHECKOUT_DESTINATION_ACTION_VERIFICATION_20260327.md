# Phase367ZE Document Preview Checkout Destination Action Verification

## Scope

- `DocumentPreview` checkout destination action
- `checkout-graph` consumption in preview node details loading

## Commands

```bash
cd ecm-frontend && ./node_modules/.bin/eslint src/components/preview/DocumentPreview.tsx src/services/nodeService.ts
cd ecm-frontend && npm run -s build
git diff --check -- \
  ecm-frontend/src/components/preview/DocumentPreview.tsx \
  docs/PHASE367ZE_DOCUMENT_PREVIEW_CHECKOUT_DESTINATION_ACTION_DEV_20260327.md \
  docs/PHASE367ZE_DOCUMENT_PREVIEW_CHECKOUT_DESTINATION_ACTION_VERIFICATION_20260327.md
```

## Result

- ESLint passed.
- Frontend production build passed.
- Targeted `git diff --check` passed.
