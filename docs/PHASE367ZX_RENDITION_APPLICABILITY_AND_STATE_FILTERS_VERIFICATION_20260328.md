# Phase367ZX Rendition Applicability And State Filters Verification

## Scope

Validate that first-class rendition resources now expose applicability metadata and support exact `state` filtering while preserving compatibility with `status=CREATED|NOT_CREATED`.

## Automated Validation

### Backend tests

Command:

```bash
cd ecm-core && mvn -q -Dtest=RenditionResourceServiceTest,RenditionResourceControllerTest,NodeControllerRelationsTest,NodeControllerLockTest,NodeControllerLockInfoTest test
```

Result:

- Passed

Covered behaviors:

- ready/registered rendition sync still works
- exact `state` filtering works
- generic binary content becomes not-applicable instead of `NOT_CREATED`
- controller forwards `status` and `state`
- controller responses include `applicable` and `applicabilityReason`
- adjacent node-controller relation and lock tests still pass

### Diff hygiene

Command:

```bash
git diff --check -- \
  ecm-core/src/main/java/com/ecm/core/entity/RenditionResource.java \
  ecm-core/src/main/java/com/ecm/core/service/RenditionResourceSyncService.java \
  ecm-core/src/main/java/com/ecm/core/service/RenditionResourceService.java \
  ecm-core/src/main/java/com/ecm/core/controller/RenditionResourceController.java \
  ecm-core/src/main/resources/db/changelog/db.changelog-master.xml \
  ecm-core/src/main/resources/db/changelog/changes/036-add-rendition-resource-applicability-columns.xml \
  ecm-core/src/test/java/com/ecm/core/service/RenditionResourceServiceTest.java \
  ecm-core/src/test/java/com/ecm/core/controller/RenditionResourceControllerTest.java
```

Result:

- Passed

## Expected Functional Outcome

- `/api/v1/nodes/{nodeId}/renditions` returns applicability metadata per resource
- not-applicable resources are visible in `ALL` but excluded from `NOT_CREATED`
- exact state filtering is available for operator tooling and future UI consumption

## Residual Gap

This phase improves live-node rendition API precision, but Athena still does not have a full registry-backed rendition definition layer or a complete migration of search/index preview semantics onto rendition resources.
