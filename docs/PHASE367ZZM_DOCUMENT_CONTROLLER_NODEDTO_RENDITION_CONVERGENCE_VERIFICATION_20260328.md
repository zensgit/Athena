# Phase367ZZM - Document Controller NodeDto Rendition Convergence Verification

## Scope

Validate that `DocumentController` mutation responses now prefer rendition summary semantics.

## Checks

### 1. Backend tests

Command:

```bash
cd ecm-core && mvn -q -Dtest=DocumentControllerCheckoutTest,DocumentControllerPreviewRepairTest test
```

Result:

- Pass

### 2. Patch hygiene

Command:

```bash
git diff --check -- \
  ecm-core/src/main/java/com/ecm/core/controller/DocumentController.java \
  ecm-core/src/test/java/com/ecm/core/controller/DocumentControllerCheckoutTest.java \
  ecm-core/src/test/java/com/ecm/core/controller/DocumentControllerPreviewRepairTest.java \
  docs/PHASE367ZZM_DOCUMENT_CONTROLLER_NODEDTO_RENDITION_CONVERGENCE_DEV_20260328.md \
  docs/PHASE367ZZM_DOCUMENT_CONTROLLER_NODEDTO_RENDITION_CONVERGENCE_VERIFICATION_20260328.md
```

Result:

- Pass

## Outcome

- checkout/checkin/cancel-checkout responses now prefer rendition summary preview semantics
- document mutation payloads no longer lag behind node read payloads on preview state reporting
