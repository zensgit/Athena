# Phase367K FileList Checkout Actions Verification

## Verified Behavior

- `Check Out` appears for writable document rows that are not checked out
- `Check Out` is disabled when a foreign active lock blocks checkout
- `Cancel Checkout` appears for writable checked-out document rows
- `Cancel Checkout` is disabled for foreign checkout when the user is not admin
- successful actions refresh the current folder so checkout badges and menu rules update immediately

## Commands

```bash
cd ecm-frontend && ./node_modules/.bin/eslint src/components/browser/FileList.tsx src/services/nodeService.ts src/utils/fileCheckoutBadgeUtils.ts src/utils/fileCheckoutBadgeUtils.test.ts
cd ecm-frontend && CI=true npm test -- --watch=false --runInBand src/utils/fileCheckoutBadgeUtils.test.ts
cd ecm-frontend && npm run -s build
git diff --check -- ecm-frontend/src/components/browser/FileList.tsx ecm-frontend/src/services/nodeService.ts ecm-frontend/src/utils/fileCheckoutBadgeUtils.ts ecm-frontend/src/utils/fileCheckoutBadgeUtils.test.ts docs/PHASE367K_FILELIST_CHECKOUT_ACTIONS_DEV_20260326.md docs/PHASE367K_FILELIST_CHECKOUT_ACTIONS_VERIFICATION_20260326.md
```

## Result

- ESLint passed
- Jest passed
- production build passed
- targeted `git diff --check` passed
