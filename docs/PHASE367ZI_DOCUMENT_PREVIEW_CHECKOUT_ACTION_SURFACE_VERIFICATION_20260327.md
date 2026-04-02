# Phase367ZI Document Preview Checkout Action Surface Verification

## Scope

- `DocumentPreview` checkout/checkin/cancel-checkout actions
- `DocumentPreview` check-in dialog

## Commands

```bash
cd ecm-frontend && ./node_modules/.bin/eslint src/components/preview/DocumentPreview.tsx src/services/nodeService.ts
cd ecm-frontend && npm run -s build
git diff --check -- \
  ecm-frontend/src/components/preview/DocumentPreview.tsx \
  docs/PHASE367ZI_DOCUMENT_PREVIEW_CHECKOUT_ACTION_SURFACE_DEV_20260327.md \
  docs/PHASE367ZI_DOCUMENT_PREVIEW_CHECKOUT_ACTION_SURFACE_VERIFICATION_20260327.md
```

## Result

- ESLint passed.
- Frontend production build passed.
- Targeted `git diff --check` passed.
