# P4 PR-25 File Plan And Category Edit-Delete Design

## Goal

Close the most needed RM admin workflow gap without reopening repository-correctness risks around subtree path updates or declared-record metadata drift.

This slice adds safe edit-delete operations for:

- file plans
- record categories

It does not attempt full rename or move semantics.

## Recommendation

Implement `PR-25` as a constrained full-stack slice:

- backend: add dedicated RM `PUT/DELETE` endpoints
- frontend: add row actions on the existing RM admin page
- scope: description-only edits and blocker-driven deletes

This is the smallest useful workflow closure because the current RM page can already create and browse both resource types.

## Explicit Safety Boundaries

`PR-25` intentionally does not support:

- file plan rename
- file plan re-parent
- record category rename
- record category re-parent
- cascade delete
- delete with reassignment
- delete preview / impact analysis

These remain deferred because they are not repository-local UI problems.

### Why File Plan Rename Is Deferred

`FolderService.updateFolder(...)` can change the folder name, but it does not recursively refresh descendant `path` values the way subtree move does. A file-plan rename would therefore reopen subtree path drift.

### Why Record Category Rename / Move Is Deferred

Record-category path is duplicated into declared-record metadata:

- `rm:recordCategoryId`
- `rm:recordCategoryName`
- `rm:recordCategoryPath`

Rename or move would require:

- recursive category path repair
- record-property backfill for all assigned declared records

That is a separate governance migration, not a small admin-workflow slice.

## Backend Design

### File Plan Update

Add:

- `PUT /api/v1/records/file-plans/{folderId}`

Allowed mutation:

- `description` only

Blocked by design:

- `name`
- `parentId`
- `folderType`

Implementation stays in `RecordsManagementService` instead of generic folder APIs so RM-specific constraints stay centralized.

### File Plan Delete

Add:

- `DELETE /api/v1/records/file-plans/{folderId}`

Delete is allowed only when all of the following are true:

- target folder is a live `FILE_PLAN`
- it has no undeleted children
- it has no disposition schedule
- it has no archive policy attachment

Deletion remains non-recursive and reuses `folderService.deleteFolder(folderId, false, false)`.

### Record Category Update

Add:

- `PUT /api/v1/records/categories/{categoryId}`

Allowed mutation:

- `description` only

Blocked by design:

- `name`
- `parentId`

Root protection:

- `/Records Management` root category cannot be modified

### Record Category Delete

Add:

- `DELETE /api/v1/records/categories/{categoryId}`

Delete is allowed only when all of the following are true:

- target category is an active RM category
- it is not the `/Records Management` root
- it is a leaf category
- no undeleted node still references it

No auto-unassign is performed. If a category is still attached to a node, deletion is rejected.

### Audit

Emit dedicated RM audit events:

- `RM_FILE_PLAN_UPDATED`
- `RM_FILE_PLAN_DELETED`
- `RM_RECORD_CATEGORY_UPDATED`
- `RM_RECORD_CATEGORY_DELETED`

## Frontend Design

Extend the existing `RecordsManagementPage` instead of adding another admin route.

### File Plans

- add row actions: `Edit`, `Delete`
- reuse the existing create form as edit mode
- when editing:
  - keep `name` disabled
  - keep `parent` disabled
  - only allow description changes

### Record Categories

- add row actions: `Edit`, `Delete`
- reuse the existing create form as edit mode
- when editing:
  - keep `name` disabled
  - keep `parent` disabled
  - only allow description changes
- RM root row renders as `Protected`

### Delete UX

Use simple confirmation before delete. Backend remains authoritative for blocker checks.

## Files

Backend:

- `ecm-core/src/main/java/com/ecm/core/service/RecordsManagementService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/RecordsManagementController.java`
- `ecm-core/src/test/java/com/ecm/core/service/RecordsManagementServiceTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/RecordsManagementControllerTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/RecordsManagementControllerSecurityTest.java`

Frontend:

- `ecm-frontend/src/services/recordsManagementService.ts`
- `ecm-frontend/src/types/index.ts`
- `ecm-frontend/src/pages/RecordsManagementPage.tsx`
- `ecm-frontend/src/pages/RecordsManagementPage.test.tsx`
- `ecm-frontend/src/services/recordsManagementService.test.ts`

## Outcome

After `PR-25`, RM admins can:

- correct file-plan descriptions
- delete empty unmanaged file plans
- correct record-category descriptions
- delete unused leaf record categories

without reopening path consistency or declared-record metadata sync problems.
