# Phase367J Checkout State Projection And FileList Badge Verification

## Verified Behavior

- search projection now includes `checkedOut` and `checkoutUser`
- list and detail node mapping now preserve checkout fields in the frontend
- `FileList` renders a checkout badge when a node is checked out
- browse actions are blocked when another user owns the checkout:
  - `Annotate (PDF)`
  - `Edit Online`
  - `Move`
  - `Delete`
- checkout owner-aware tooltip copy is shown for disabled actions

## Commands

```bash
cd ecm-core && mvn -q -Dtest=NodeDocumentLockProjectionTest,NodeDocumentCheckoutProjectionTest test
cd ecm-frontend && ./node_modules/.bin/eslint src/components/browser/FileList.tsx src/services/nodeService.ts src/utils/fileCheckoutBadgeUtils.ts src/utils/fileCheckoutBadgeUtils.test.ts
cd ecm-frontend && CI=true npm test -- --watch=false --runInBand src/utils/fileCheckoutBadgeUtils.test.ts
cd ecm-frontend && npm run -s build
git diff --check -- ecm-core/src/main/java/com/ecm/core/search/NodeDocument.java ecm-core/src/test/java/com/ecm/core/search/NodeDocumentCheckoutProjectionTest.java ecm-frontend/src/services/nodeService.ts ecm-frontend/src/components/browser/FileList.tsx ecm-frontend/src/utils/fileCheckoutBadgeUtils.ts ecm-frontend/src/utils/fileCheckoutBadgeUtils.test.ts docs/PHASE367J_CHECKOUT_STATE_PROJECTION_AND_FILELIST_BADGE_DEV_20260326.md docs/PHASE367J_CHECKOUT_STATE_PROJECTION_AND_FILELIST_BADGE_VERIFICATION_20260326.md
```

## Result

- backend tests passed
- frontend ESLint passed
- frontend Jest passed
- frontend build passed
- targeted `git diff --check` passed
