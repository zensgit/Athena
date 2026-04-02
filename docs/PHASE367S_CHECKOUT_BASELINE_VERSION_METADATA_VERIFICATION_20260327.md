# Phase367S Verification: Checkout Baseline Version Metadata

## Verified

- Checkout now persists baseline version id/label metadata.
- Cancel checkout clears baseline metadata.
- Keep-checked-out refreshes baseline metadata to the current checked-in version.
- Virtual checkout relation returns baseline version metadata.
- `AdvancedSearchPage` relation details show baseline/current version checkout lineage.

## Commands

```bash
cd ecm-core && mvn -q -Dtest=NodeServiceCheckoutTest,DocumentControllerCheckoutTest,NodeControllerRelationsTest test
cd ecm-frontend && ./node_modules/.bin/eslint src/pages/AdvancedSearchPage.tsx src/services/nodeService.ts
cd ecm-frontend && npm run -s build
git diff --check -- ecm-core/src/main/java/com/ecm/core/entity/Document.java ecm-core/src/main/resources/db/changelog/changes/035-add-document-checkout-baseline-columns.xml ecm-core/src/main/resources/db/changelog/db.changelog-master.xml ecm-core/src/main/java/com/ecm/core/controller/NodeController.java ecm-core/src/test/java/com/ecm/core/service/NodeServiceCheckoutTest.java ecm-core/src/test/java/com/ecm/core/controller/NodeControllerRelationsTest.java ecm-frontend/src/services/nodeService.ts ecm-frontend/src/pages/AdvancedSearchPage.tsx docs/PHASE367S_CHECKOUT_BASELINE_VERSION_METADATA_DEV_20260327.md docs/PHASE367S_CHECKOUT_BASELINE_VERSION_METADATA_VERIFICATION_20260327.md
```

## Notes

- This phase does not yet create a separate working-copy node.
- This phase does not yet support checkout-to-destination folders.
- This phase does not yet create a bidirectional source/working-copy graph; it provides baseline/source metadata only.
