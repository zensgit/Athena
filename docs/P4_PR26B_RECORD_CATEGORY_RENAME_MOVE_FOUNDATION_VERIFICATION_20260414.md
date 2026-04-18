# P4 PR-26B Record Category Rename-Move Foundation Verification

## Implementation Summary

`PR-26B` was implemented as a backend-only RM slice.

Delivered behavior:

- dedicated RM endpoints now support record-category rename and move
- RM root category remains protected from rename and move
- cycle / self-parent moves are rejected before mutation
- descendant category paths are repaired recursively after rename and move
- declared-record metadata is repaired for the affected category subtree:
  - `rm:recordCategoryName`
  - `rm:recordCategoryPath`
- affected documents are reindexed through an after-commit batch reindex event
- frontend rename / move entry points remain intentionally deferred

## Files Changed

Backend:

- `ecm-core/src/main/java/com/ecm/core/service/RecordsManagementService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/RecordsManagementController.java`
- `ecm-core/src/main/java/com/ecm/core/event/NodesReindexRequestedEvent.java`
- `ecm-core/src/main/java/com/ecm/core/event/EcmEventListener.java`
- `ecm-core/src/main/java/com/ecm/core/search/SearchIndexService.java`
- `ecm-core/src/test/java/com/ecm/core/service/RecordsManagementServiceTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/RecordsManagementControllerTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/RecordsManagementControllerSecurityTest.java`
- `ecm-core/src/test/java/com/ecm/core/event/EcmEventListenerPermissionIndexingTest.java`

No frontend files changed in this slice.

## Targeted Validation

Command:

```bash
./ecm-core/mvnw -B -Dstyle.color=never test -Dtest=RecordsManagementServiceTest,RecordsManagementControllerTest,RecordsManagementControllerSecurityTest,EcmEventListenerPermissionIndexingTest
```

Result:

- `Tests run: 58`
- `Failures: 0`
- `Errors: 0`
- `Skipped: 0`

Coverage added in this slice includes:

- record-category rename repairs descendant category paths
- record-category rename repairs declared-record metadata
- record-category move repairs descendant category paths under a new parent
- record-category move rejects cycle creation
- rename / move controller contracts
- rename / move security gates
- node batch reindex listener behavior

## Full Regression

Command:

```bash
./ecm-core/mvnw -B -Dstyle.color=never test
```

Result:

- `Tests run: 1568`
- `Failures: 0`
- `Errors: 0`
- `Skipped: 11`

Because this slice is backend-only, no frontend test or build rerun is required.

## Static Checks

Command:

```bash
git diff --check
```

Result:

- passed

## Verification Conclusion

`PR-26B` can be treated as approved for backend correctness:

- category-tree rename / move no longer leaves descendant path drift
- declared-record fallback metadata no longer drifts from the category tree
- affected search documents are reindexed automatically after commit
- UI exposure remains intentionally deferred to a later thin RM admin slice
