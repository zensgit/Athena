# P4 PR-26A File Plan Rename-Move Foundation Verification

## Implementation Summary

`PR-26A` was implemented as a backend-only RM slice.

Delivered behavior:

- dedicated RM endpoints now support file-plan rename and file-plan move
- file-plan rename repairs descendant `path` values recursively from the renamed root
- file-plan rename publishes a subtree reindex request instead of relying on generic node update behavior
- file-plan move reuses existing `NodeService.moveNode(...)` subtree repair + subtree reindex behavior
- rename and move are both audited through RM-specific audit events
- frontend rename / move entry points remain intentionally deferred

## Files Changed

Backend:

- `ecm-core/src/main/java/com/ecm/core/service/RecordsManagementService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/RecordsManagementController.java`
- `ecm-core/src/main/java/com/ecm/core/event/NodeSubtreeReindexRequestedEvent.java`
- `ecm-core/src/main/java/com/ecm/core/event/EcmEventListener.java`
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

- `Tests run: 52`
- `Failures: 0`
- `Errors: 0`
- `Skipped: 0`

Coverage added in this slice includes:

- file-plan rename repairs descendant paths
- file-plan rename requests subtree reindex
- file-plan move validates RM parent placement before mutation
- file-plan move delegates to authoritative node move semantics
- rename / move controller contracts
- rename / move security gates
- subtree reindex event listener behavior

## Full Regression

Command:

```bash
./ecm-core/mvnw -B -Dstyle.color=never test
```

Result:

- `Tests run: 1562`
- `Failures: 0`
- `Errors: 0`
- `Skipped: 11`

Because this slice is backend-only, no frontend test or build rerun was required.

## Static Checks

Command:

```bash
git diff --check
```

Result:

- passed

## Verification Conclusion

`PR-26A` can be treated as approved for backend correctness:

- rename no longer leaves subtree path drift in the database
- rename no longer relies on stale index paths
- move remains on the existing authoritative subtree repair chain
- UI exposure is still intentionally deferred until a later RM admin workflow slice
