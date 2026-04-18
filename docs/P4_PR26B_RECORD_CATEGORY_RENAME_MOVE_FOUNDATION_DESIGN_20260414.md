# P4 PR-26B Record Category Rename-Move Foundation Design

## Goal

Close the remaining record-category rename / re-parent correctness gap without reopening the RM admin UI before the backend contract is trustworthy.

This slice is intentionally backend-only.

## Scope

`PR-26B` covers:

- record-category rename through a dedicated RM API
- record-category move through a dedicated RM API
- cycle and root-protection validation
- recursive descendant category path repair
- declared-record metadata repair for the affected category subtree
- affected-node reindex after metadata repair
- RM audit events for category rename and move

`PR-26B` does not cover:

- RM admin UI rename / move entry points
- drag-and-drop taxonomy editing
- delete with reassignment
- taxonomy merge workflows
- impact preview UI

## Recommendation

Keep record-category rename / move inside `RecordsManagementService`.

This is not just category-tree editing. The operation fans out into:

- descendant `Category.path` repair
- declared-record `rm:recordCategoryName` repair
- declared-record `rm:recordCategoryPath` repair
- search/index refresh for affected nodes

Routing admins through generic category seams would not carry those guarantees.

## Why Backend-Only

The current RM admin page intentionally limits category edit to `description` only and explicitly states that rename / re-parent remain blocked until path and metadata sync are hardened.

That boundary is still correct. This slice first makes the backend authoritative by adding:

- explicit RM endpoints
- property repair for declared records
- affected-node reindex
- regression coverage

Frontend affordances can be added later as a separate thin slice on top of the hardened backend contract.

## Backend Design

### Rename

Add:

- `PUT /api/v1/records/categories/{categoryId}/rename`

Behavior:

1. require RM admin
2. load the active record category
3. reject RM root
4. reject blank name
5. no-op if the requested name is unchanged
6. rename the root category node
7. recursively repair descendant category paths
8. repair declared-record metadata for documents assigned to the renamed subtree
9. publish affected-node reindex after commit
10. emit `RM_RECORD_CATEGORY_RENAMED`

### Move

Add:

- `PUT /api/v1/records/categories/{categoryId}/move`

Behavior:

1. require RM admin
2. require non-null `targetParentId`
3. load active target parent inside the RM tree
4. reject RM root mutation
5. reject cycles and self-parenting
6. no-op if the parent is unchanged
7. move the category root
8. recursively repair descendant category paths
9. repair declared-record metadata for documents assigned to the moved subtree
10. publish affected-node reindex after commit
11. emit `RM_RECORD_CATEGORY_MOVED`

## Metadata Repair Shape

The authoritative category relation stays on `Node.categories`, but declared records also duplicate RM category state into node properties:

- `rm:recordCategoryId`
- `rm:recordCategoryName`
- `rm:recordCategoryPath`

This slice keeps `rm:recordCategoryId` stable and rewrites:

- `rm:recordCategoryName`
- `rm:recordCategoryPath`

for every declared record assigned to the mutated category subtree.

## Reindex Shape

Because search stores category names and indexable properties on `NodeDocument`, repairing database metadata alone is not enough.

This slice therefore adds a dedicated batch reindex event:

- `NodesReindexRequestedEvent`

Listener behavior:

- `EcmEventListener.handleNodesReindexRequested(...)` calls `SearchIndexService.reindexNodes(...)`

This keeps reindex work after commit and avoids reusing `NodeUpdatedEvent`, which would have created unrelated node-audit noise.

## Files

Backend production:

- `ecm-core/src/main/java/com/ecm/core/service/RecordsManagementService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/RecordsManagementController.java`
- `ecm-core/src/main/java/com/ecm/core/event/NodesReindexRequestedEvent.java`
- `ecm-core/src/main/java/com/ecm/core/event/EcmEventListener.java`
- `ecm-core/src/main/java/com/ecm/core/search/SearchIndexService.java`

Backend tests:

- `ecm-core/src/test/java/com/ecm/core/service/RecordsManagementServiceTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/RecordsManagementControllerTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/RecordsManagementControllerSecurityTest.java`
- `ecm-core/src/test/java/com/ecm/core/event/EcmEventListenerPermissionIndexingTest.java`

## Outcome

After `PR-26B`, Athena has an authoritative RM backend contract for record-category rename / move that:

- keeps the category subtree path-consistent
- keeps declared-record fallback metadata consistent
- refreshes affected search documents automatically
- still leaves UI exposure as a separate, lower-risk follow-up
