# Phase367ZZO - Content Type Controller Rendition Convergence Verification

## Scope

Validate that `ContentTypeController.applyType(...)` now returns rendition-backed preview semantics.

## Checks

### 1. Backend tests

Command:

```bash
cd ecm-core && mvn -q -Dtest=ContentTypeControllerPreviewSemanticsTest test
```

Result:

- Pass

### 2. Patch hygiene

Command:

```bash
git diff --check -- \
  ecm-core/src/main/java/com/ecm/core/controller/ContentTypeController.java \
  ecm-core/src/test/java/com/ecm/core/controller/ContentTypeControllerPreviewSemanticsTest.java \
  docs/PHASE367ZZO_CONTENT_TYPE_CONTROLLER_RENDITION_CONVERGENCE_DEV_20260328.md \
  docs/PHASE367ZZO_CONTENT_TYPE_CONTROLLER_RENDITION_CONVERGENCE_VERIFICATION_20260328.md
```

Result:

- Pass

## Outcome

- `applyType` no longer returns raw entity-derived preview semantics
- controller-level `NodeDto.from(...)` mutation exits are now effectively eliminated from the node/document/type surfaces
