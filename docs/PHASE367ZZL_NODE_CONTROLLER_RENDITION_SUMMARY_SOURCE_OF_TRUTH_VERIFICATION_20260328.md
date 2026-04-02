# Phase367ZZL - Node Controller Rendition Summary Source Of Truth Verification

## Scope

Validate that ordinary node endpoints now prefer rendition summary preview semantics for detail and list payloads.

## Checks

### 1. Backend tests

Command:

```bash
cd ecm-core && mvn -q -Dtest=NodeControllerPreviewSemanticsTest,NodeControllerRelationsTest test
```

Result:

- Pass

### 2. Patch hygiene

Command:

```bash
git diff --check -- \
  ecm-core/src/main/java/com/ecm/core/dto/NodeDto.java \
  ecm-core/src/main/java/com/ecm/core/controller/NodeController.java \
  ecm-core/src/test/java/com/ecm/core/controller/NodeControllerPreviewSemanticsTest.java \
  docs/PHASE367ZZL_NODE_CONTROLLER_RENDITION_SUMMARY_SOURCE_OF_TRUTH_DEV_20260328.md \
  docs/PHASE367ZZL_NODE_CONTROLLER_RENDITION_SUMMARY_SOURCE_OF_TRUTH_VERIFICATION_20260328.md
```

Result:

- Pass

## Outcome

- `GET /nodes/{id}` now prefers rendition summary preview semantics
- `GET /nodes/{id}/children` now prefers rendition summary preview semantics
- ordinary node payloads no longer lag behind rendition-specific surfaces on preview state reporting
