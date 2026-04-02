# Phase367I Checkout Info Operator Diagnostics Verification

## Verified Behavior

- writable unlocked documents return `AVAILABLE` with `canCheckout=true`
- checkout status distinguishes current-user ownership from foreign checkout ownership
- admin affordances for foreign checkout are surfaced through `canCheckIn` and `canCancelCheckout`
- foreign active locks can block checkout even when the document itself is not checked out
- `DocumentPreview` now shows checkout chips and alert text from the new diagnostics endpoint

## Commands

```bash
cd ecm-core && mvn -q -Dtest=NodeServiceCheckoutTest,DocumentControllerCheckoutTest test
cd ecm-frontend && ./node_modules/.bin/eslint src/components/preview/DocumentPreview.tsx src/services/nodeService.ts src/types/index.ts src/utils/checkoutInfoUtils.ts src/utils/checkoutInfoUtils.test.ts
cd ecm-frontend && CI=true npm test -- --watch=false --runInBand src/utils/checkoutInfoUtils.test.ts
cd ecm-frontend && npm run -s build
git diff --check -- ecm-core/src/main/java/com/ecm/core/entity/CheckoutStatus.java ecm-core/src/main/java/com/ecm/core/dto/CheckoutInfoDto.java ecm-core/src/main/java/com/ecm/core/service/NodeService.java ecm-core/src/main/java/com/ecm/core/controller/DocumentController.java ecm-core/src/test/java/com/ecm/core/service/NodeServiceCheckoutTest.java ecm-core/src/test/java/com/ecm/core/controller/DocumentControllerCheckoutTest.java ecm-frontend/src/types/index.ts ecm-frontend/src/services/nodeService.ts ecm-frontend/src/utils/checkoutInfoUtils.ts ecm-frontend/src/utils/checkoutInfoUtils.test.ts ecm-frontend/src/components/preview/DocumentPreview.tsx docs/PHASE367I_CHECKOUT_INFO_OPERATOR_DIAGNOSTICS_DEV_20260326.md docs/PHASE367I_CHECKOUT_INFO_OPERATOR_DIAGNOSTICS_VERIFICATION_20260326.md
```

## Result

- backend tests passed
- frontend ESLint passed
- frontend Jest passed
- frontend build passed
- targeted `git diff --check` passed
