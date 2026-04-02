# Phase368ZB Verification

## Scope

Validated shared preview mutation status convergence in:

- `RenditionResourceService`
- `DocumentController`
- `RenditionResourceController`

## Commands

### Targeted backend regression

```bash
cd ecm-core && mvn -q -Dtest=RenditionResourceServiceTest,DocumentControllerPreviewRepairTest,RenditionResourceControllerTest test
```

Result:

- passed

### Patch sanity

```bash
git diff --check -- \
  ecm-core/src/main/java/com/ecm/core/service/RenditionResourceService.java \
  ecm-core/src/main/java/com/ecm/core/controller/DocumentController.java \
  ecm-core/src/main/java/com/ecm/core/controller/RenditionResourceController.java \
  ecm-core/src/test/java/com/ecm/core/service/RenditionResourceServiceTest.java \
  ecm-core/src/test/java/com/ecm/core/controller/DocumentControllerPreviewRepairTest.java \
  ecm-core/src/test/java/com/ecm/core/controller/RenditionResourceControllerTest.java
```

Result:

- passed

## Verified behavior

- `PreviewMutationStatus` prefers effective preview summary over raw queue status
- document preview queue/repair endpoints now use the shared mutation contract
- rendition mutation queue-status payload now uses the same shared mutation contract

## Notes

This phase intentionally kept response JSON shapes stable. Verification focused on contract convergence, not API redesign.
