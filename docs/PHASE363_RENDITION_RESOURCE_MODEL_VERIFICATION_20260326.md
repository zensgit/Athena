# Phase 363 Verification

## Scope

Verify the first-class rendition resource model slice and ensure the previous async governance work still holds.

## Verified Commands

### Backend targeted tests

```bash
cd ecm-core && mvn -q -Dtest=RenditionResourceServiceTest,RenditionResourceControllerTest,AsyncTaskAcknowledgementServiceTest,AsyncTaskLifecycleServiceTest,AnalyticsControllerTest,AnalyticsControllerSecurityTest test
```

Result:

- passed

Coverage included:

- rendition resource mirroring for document preview/thumbnail state
- rendition API list/get responses
- prior async acknowledgement and lifecycle contract regression coverage

### Patch hygiene

```bash
git diff --check -- \
  ecm-core/src/main/java/com/ecm/core/entity/RenditionState.java \
  ecm-core/src/main/java/com/ecm/core/entity/RenditionResource.java \
  ecm-core/src/main/java/com/ecm/core/repository/RenditionResourceRepository.java \
  ecm-core/src/main/java/com/ecm/core/service/RenditionResourceService.java \
  ecm-core/src/main/java/com/ecm/core/controller/RenditionResourceController.java \
  ecm-core/src/main/resources/db/changelog/changes/033-add-rendition-resources-table.xml \
  ecm-core/src/main/resources/db/changelog/db.changelog-master.xml \
  ecm-core/src/test/java/com/ecm/core/service/RenditionResourceServiceTest.java \
  ecm-core/src/test/java/com/ecm/core/controller/RenditionResourceControllerTest.java
```

Result:

- passed

## Manual Expectations

- document nodes now expose first-class `preview` and `thumbnail` resources under `/api/v1/nodes/{nodeId}/renditions`
- resources can be returned even when not yet created, using `REGISTERED` state
- the resource model is stored in `rendition_resources`, not only derived ad hoc per response
- old `/relations/renditions` endpoints remain untouched for compatibility

## Remaining Risk

- this phase is still mirroring from `Document.preview*`, so it is not yet a full source-of-truth migration
- no mutation endpoints exist yet for `requeue`, `invalidate`, or `create`
- no rendition definition registry exists yet, so applicability is still heuristic and intentionally conservative
