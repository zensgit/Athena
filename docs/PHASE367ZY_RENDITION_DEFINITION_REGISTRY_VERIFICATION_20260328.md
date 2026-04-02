# Phase367ZY Rendition Definition Registry Verification

## Scope

Validate that Athena now exposes a registry-backed rendition definition surface and persists generation/dependency metadata on first-class rendition resources.

## Automated Validation

### Backend tests

Command:

```bash
cd ecm-core && mvn -q -Dtest=RenditionResourceSyncServiceTest,RenditionResourceServiceTest,RenditionResourceControllerTest,NodeControllerRelationsTest,NodeControllerLockTest,NodeControllerLockInfoTest test
```

Result:

- Passed

Covered behaviors:

- sync still mirrors preview/thumbnail lifecycle state
- synced resources now carry `generationMode`
- thumbnail resources carry `dependencyRenditionKey=preview`
- registry-backed definition status can be listed per node
- `/api/v1/nodes/{nodeId}/renditions` exposes generation/dependency metadata
- `/api/v1/nodes/{nodeId}/renditions/definitions` exposes registry-backed status rows
- adjacent node relation and lock controller tests still pass

### Diff hygiene

Command:

```bash
git diff --check -- \
  ecm-core/src/main/java/com/ecm/core/service/RenditionDefinitionRegistry.java \
  ecm-core/src/main/java/com/ecm/core/service/RenditionResourceSyncService.java \
  ecm-core/src/main/java/com/ecm/core/service/RenditionResourceService.java \
  ecm-core/src/main/java/com/ecm/core/controller/RenditionResourceController.java \
  ecm-core/src/main/java/com/ecm/core/entity/RenditionResource.java \
  ecm-core/src/main/resources/db/changelog/changes/037-add-rendition-resource-definition-columns.xml \
  ecm-core/src/main/resources/db/changelog/db.changelog-master.xml \
  ecm-core/src/test/java/com/ecm/core/service/RenditionResourceSyncServiceTest.java \
  ecm-core/src/test/java/com/ecm/core/service/RenditionResourceServiceTest.java \
  ecm-core/src/test/java/com/ecm/core/controller/RenditionResourceControllerTest.java \
  docs/PHASE367ZY_RENDITION_DEFINITION_REGISTRY_DEV_20260328.md \
  docs/PHASE367ZY_RENDITION_DEFINITION_REGISTRY_VERIFICATION_20260328.md
```

Result:

- Pending until after doc write, then expected to pass

## Expected Functional Outcome

- `GET /api/v1/nodes/{nodeId}/renditions` returns `generationMode` and `dependencyRenditionKey`
- `GET /api/v1/nodes/{nodeId}/renditions/definitions` returns registered rendition definitions with applicability and current state
- preview/thumbnail definition semantics are now explicit instead of being implicit sync heuristics only

## Residual Gap

This phase gives Athena a real bounded definition registry, but not yet:

- dynamic definition registration
- full search/index convergence on rendition resource semantics
- full lifecycle source-of-truth migration away from `Document.preview*`
