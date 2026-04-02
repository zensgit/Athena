# Phase367ZV Node Rendition Relation Service Convergence Verification

## Scope

Validate that legacy node rendition relation endpoints now consume `RenditionResourceService` and that the change does not break adjacent node-controller flows.

## Automated Validation

### Backend tests

Command:

```bash
cd ecm-core && mvn -q -Dtest=RenditionResourceServiceTest,RenditionResourceControllerTest,NodeControllerRelationsTest,NodeControllerLockTest,NodeControllerLockInfoTest test
```

Result:

- Passed

Coverage in this run:

- `RenditionResourceServiceTest`
  - validates mirrored rendition resources
  - validates created/not-created filtering
  - validates summary derivation from preview rendition resource
  - validates invalidate/requeue behavior
- `RenditionResourceControllerTest`
  - validates first-class rendition resource API responses
- `NodeControllerRelationsTest`
  - validates legacy relation endpoints now map rendition resources
  - validates rendition relation summary reuse of service summary
  - validates checkout graph/version relation regressions still pass
- `NodeControllerLockTest`
  - validates lock endpoint wiring after constructor dependency expansion
- `NodeControllerLockInfoTest`
  - validates lock-info endpoint wiring after constructor dependency expansion

### Diff hygiene

Command:

```bash
git diff --check -- \
  ecm-core/src/main/java/com/ecm/core/service/RenditionResourceService.java \
  ecm-core/src/main/java/com/ecm/core/controller/NodeController.java \
  ecm-core/src/test/java/com/ecm/core/service/RenditionResourceServiceTest.java \
  ecm-core/src/test/java/com/ecm/core/controller/NodeControllerRelationsTest.java \
  ecm-core/src/test/java/com/ecm/core/controller/NodeControllerLockTest.java \
  ecm-core/src/test/java/com/ecm/core/controller/NodeControllerLockInfoTest.java
```

Result:

- Passed

## Expected Functional Outcome

- `/api/v1/nodes/{nodeId}/relations/renditions` returns rendition rows derived from `RenditionResource`.
- `/api/v1/nodes/{nodeId}/relations/renditions/summary` reuses the same preview summary semantics as the first-class rendition path.
- `/api/v1/nodes/{nodeId}/relations/summary` no longer computes rendition availability directly from `Document.previewStatus`.
- Lock-related node controller tests still pass after injecting the new rendition service dependency.

## Residual Gap

This phase converges legacy node rendition relation endpoints onto the new rendition model, but it does not yet migrate search indexing or all operator surfaces off raw `Document.preview*` fields.
