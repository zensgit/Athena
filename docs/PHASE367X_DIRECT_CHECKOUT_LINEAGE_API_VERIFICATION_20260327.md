# Phase367X Verification: Direct Checkout Lineage API

## Verified

- `GET /api/v1/documents/{documentId}/checkout-lineage` returns checkout info plus baseline/current versions.
- `DocumentControllerCheckoutTest` covers the new endpoint.
- `VersionHistoryDialog` can use direct lineage payloads for compare even when version rows are not yet loaded.

## Commands

```bash
cd ecm-core && mvn -q -Dtest=DocumentControllerCheckoutTest test
cd ecm-frontend && ./node_modules/.bin/eslint src/components/dialogs/VersionHistoryDialog.tsx src/services/nodeService.ts
cd ecm-frontend && npm run -s build
git diff --check -- ecm-core/src/main/java/com/ecm/core/dto/CheckoutLineageDto.java ecm-core/src/main/java/com/ecm/core/controller/DocumentController.java ecm-core/src/test/java/com/ecm/core/controller/DocumentControllerCheckoutTest.java ecm-frontend/src/services/nodeService.ts ecm-frontend/src/components/dialogs/VersionHistoryDialog.tsx docs/PHASE367X_DIRECT_CHECKOUT_LINEAGE_API_DEV_20260327.md docs/PHASE367X_DIRECT_CHECKOUT_LINEAGE_API_VERIFICATION_20260327.md
```

## Notes

- This phase still does not create separate working-copy nodes.
- This phase strengthens direct lineage retrieval, not entity-level working-copy graph semantics.
