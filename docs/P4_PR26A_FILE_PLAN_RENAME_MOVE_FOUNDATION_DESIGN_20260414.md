# P4 PR-26A File Plan Rename-Move Foundation Design

## Goal

Close the remaining file-plan rename / re-parent correctness gap without reopening the broader record-category metadata repair problem.

This slice is intentionally backend-only.

## Scope

`PR-26A` covers:

- file-plan rename through a dedicated RM API
- file-plan re-parent through a dedicated RM API
- recursive persisted `path` repair for the full file-plan subtree after rename
- subtree search reindex after rename
- RM audit events for rename and move

`PR-26A` does not cover:

- record-category rename or re-parent
- RM admin UI entry points for file-plan rename / move
- batch restructure workflows
- drag-and-drop RM tree editing
- move-to-root semantics

## Recommendation

Do not route admins through generic folder controllers.

Keep file-plan rename / move inside `RecordsManagementService` so RM placement rules, audit semantics, and subtree consistency handling stay centralized.

## Why Backend-Only

The current RM admin page intentionally exposed only safe description edit / delete flows in `PR-25`.

Adding a frontend rename / move affordance before the backend contract existed would have encouraged use of generic node/folder mutation APIs that do not carry RM-specific audit and parent-validation semantics.

This slice therefore ships:

- authoritative backend API
- regression coverage
- no UI affordance yet

## Backend Design

### Rename

Add:

- `PUT /api/v1/records/file-plans/{folderId}/rename`

Behavior:

1. require RM admin
2. load the live `FILE_PLAN`
3. reject blank names
4. no-op when the requested name is unchanged
5. delegate root-folder rename to `FolderService.updateFolder(...)`
6. recursively refresh descendant `path` values from the renamed root down
7. publish subtree reindex through `NodeSubtreeReindexRequestedEvent`
8. emit `RM_FILE_PLAN_RENAMED`

### Move

Add:

- `PUT /api/v1/records/file-plans/{folderId}/move`

Behavior:

1. require RM admin
2. require non-null `targetParentId`
3. validate the parent with existing RM placement rules
4. no-op when the target parent is unchanged
5. delegate mutation to `NodeService.moveNode(...)`
6. rely on the existing subtree path refresh and `NodeMovedEvent -> reindexNodeSubtree(...)` chain
7. emit `RM_FILE_PLAN_MOVED`

## Event Shape

Rename needed a dedicated subtree reindex signal because generic folder update only reindexes the renamed node itself.

This slice therefore adds:

- `NodeSubtreeReindexRequestedEvent`

Listener behavior:

- `EcmEventListener.handleNodeSubtreeReindexRequested(...)` calls `searchIndexService.reindexNodeSubtree(...)`

## Files

Backend production:

- `ecm-core/src/main/java/com/ecm/core/service/RecordsManagementService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/RecordsManagementController.java`
- `ecm-core/src/main/java/com/ecm/core/event/NodeSubtreeReindexRequestedEvent.java`
- `ecm-core/src/main/java/com/ecm/core/event/EcmEventListener.java`

Backend tests:

- `ecm-core/src/test/java/com/ecm/core/service/RecordsManagementServiceTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/RecordsManagementControllerTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/RecordsManagementControllerSecurityTest.java`
- `ecm-core/src/test/java/com/ecm/core/event/EcmEventListenerPermissionIndexingTest.java`

## Known Boundary

`moveFilePlan(...)` currently reuses `NodeService.moveNode(...)` and therefore inherits existing RM mutation guardrails for governed descendants. This slice does not redefine that policy.

If product later wants different semantics for moving file plans that contain declared-record subtrees, that should be a follow-up policy decision instead of being smuggled into the rename/move foundation.

## Outcome

After `PR-26A`, Athena has an authoritative RM backend path for file-plan rename / move that:

- keeps subtree DB paths coherent
- refreshes subtree search state
- preserves RM auditability
- leaves `PR-26B` record-category metadata repair isolated as a separate risk surface
