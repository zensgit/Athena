# Phase367ZZN - Node Controller Mutation Rendition Convergence Verification

## Scope

Validate that `NodeController` mutation responses and search reads use the shared rendition-backed preview DTO path.

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
  ecm-core/src/main/java/com/ecm/core/controller/NodeController.java \
  ecm-core/src/test/java/com/ecm/core/controller/NodeControllerPreviewSemanticsTest.java \
  docs/PHASE367ZZN_NODE_CONTROLLER_MUTATION_RENDITION_CONVERGENCE_DEV_20260328.md \
  docs/PHASE367ZZN_NODE_CONTROLLER_MUTATION_RENDITION_CONVERGENCE_VERIFICATION_20260328.md
```

Result:

- Pass

## Outcome

- node create/update/move/copy responses now use rendition-backed preview semantics
- node search reads are explicitly covered by the preview semantics regression suite
