# Phase367ZF FileList Checkout Destination Action Verification

## Scope

- `FileList` checkout destination context action

## Commands

```bash
cd ecm-frontend && ./node_modules/.bin/eslint src/components/browser/FileList.tsx
cd ecm-frontend && npm run -s build
git diff --check -- \
  ecm-frontend/src/components/browser/FileList.tsx \
  docs/PHASE367ZF_FILELIST_CHECKOUT_DESTINATION_ACTION_DEV_20260327.md \
  docs/PHASE367ZF_FILELIST_CHECKOUT_DESTINATION_ACTION_VERIFICATION_20260327.md
```

## Result

- ESLint passed.
- Frontend production build passed.
- Targeted `git diff --check` passed.
