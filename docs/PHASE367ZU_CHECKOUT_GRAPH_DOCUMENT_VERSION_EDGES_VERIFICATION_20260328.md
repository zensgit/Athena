# Phase367ZU Checkout Graph Document Version Edges Verification

## Scope

- backend checkout-graph edge enrichment
- active-checkout graph regression
- available-document current-version edge regression

## Commands

```bash
cd ecm-core && mvn -q -Dtest=NodeControllerRelationsTest test
git diff --check -- \
  ecm-core/src/main/java/com/ecm/core/controller/NodeController.java \
  ecm-core/src/test/java/com/ecm/core/controller/NodeControllerRelationsTest.java \
  docs/PHASE367ZU_CHECKOUT_GRAPH_DOCUMENT_VERSION_EDGES_DEV_20260328.md \
  docs/PHASE367ZU_CHECKOUT_GRAPH_DOCUMENT_VERSION_EDGES_VERIFICATION_20260328.md
```

## Result

- Backend controller regression passed.
- Targeted `git diff --check` passed.
