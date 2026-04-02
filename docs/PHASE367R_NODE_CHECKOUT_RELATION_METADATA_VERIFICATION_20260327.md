# Phase367R Verification: Node Checkout Relation Metadata

## Verified

- Node relations summary now exposes checkout presence, owner, and timestamp.
- `GET /api/v1/nodes/{nodeId}/relations/checkout` returns caller-relative checkout affordances for documents.
- `AdvancedSearchPage` relation summary/details now display checkout relation metadata alongside versions and renditions.
- The new relation metadata reuses existing checkout diagnostics semantics instead of creating a divergent contract.

## Commands

```bash
cd ecm-core && mvn -q -Dtest=NodeControllerRelationsTest test
cd ecm-frontend && ./node_modules/.bin/eslint src/pages/AdvancedSearchPage.tsx src/services/nodeService.ts
cd ecm-frontend && npm run -s build
git diff --check -- ecm-core/src/main/java/com/ecm/core/controller/NodeController.java ecm-core/src/test/java/com/ecm/core/controller/NodeControllerRelationsTest.java ecm-frontend/src/services/nodeService.ts ecm-frontend/src/pages/AdvancedSearchPage.tsx docs/PHASE367R_NODE_CHECKOUT_RELATION_METADATA_DEV_20260327.md docs/PHASE367R_NODE_CHECKOUT_RELATION_METADATA_VERIFICATION_20260327.md
```

## Notes

- This phase is virtual checkout/source metadata, not a full working-copy entity model.
- This phase does not yet support checkout-to-destination folders.
- This phase does not yet expose a separate working-copy node lineage graph.
