# P4 PR-26D File Plan Rename-Move UI Design

## Goal

Expose the already-hardened file-plan rename / move backend contract through a thin RM admin UI slice, without reopening generic folder-tree mutation seams.

## Scope

`PR-26D` covers:

- service/type support for file-plan rename / move
- explicit RM admin dialogs for file-plan rename
- explicit RM admin dialogs for file-plan move
- RM admin table actions wired to the dedicated dialogs
- targeted page/service regression coverage

`PR-26D` does not cover:

- generic browse-page or folder-context file-plan rename / move actions
- drag-and-drop file-plan tree editing
- workspace/system-root target selection in the thin RM page
- generic folder picker integration

## Recommendation

Do not overload the existing file-plan description edit form.

Keep rename / move in explicit dialogs:

- `Rename File Plan`
- `Move File Plan`

That preserves the safe split introduced in `PR-25` and `PR-26A`: inline edit remains description-only, while subtree-affecting mutations stay clearly separated.

## Why Explicit Dialogs

File-plan rename / move is subtree fan-out behavior:

- descendant node paths change
- affected search documents are reindexed
- RM audit should reflect an explicit operator action

That deserves dedicated copy and explicit confirmation rather than silently re-enabling the blocked `name` and `parent` fields in the edit form.

## Frontend Design

### Service Layer

Add:

- `renameFilePlan(folderId, { name })`
- `moveFilePlan(folderId, { targetParentId })`

These must call the authoritative RM endpoints rather than generic folder APIs.

### Page Actions

Extend the existing file-plan row actions on `RecordsManagementPage`:

- `Edit`
- `Rename`
- `Move`
- `Delete`

`Edit` remains description-only.

### Rename Dialog

Dialog copy should explain:

- descendant node paths will be repaired
- affected search documents will be refreshed
- the RM subtree remains intact

### Move Dialog

The thin RM page only has authoritative access to the current file-plan list. In this slice, the dialog should therefore:

- exclude the current file plan
- exclude descendants of the current file plan
- exclude the current parent if that parent is itself another file plan
- require the operator to explicitly choose a new file-plan parent

Workspace/system-root targets remain deferred to a later slice that can safely reuse or narrow a broader folder picker.

## Files

Frontend production:

- `ecm-frontend/src/services/recordsManagementService.ts`
- `ecm-frontend/src/types/index.ts`
- `ecm-frontend/src/pages/RecordsManagementPage.tsx`
- `ecm-frontend/src/components/records/RenameFilePlanDialog.tsx`
- `ecm-frontend/src/components/records/MoveFilePlanDialog.tsx`

Frontend tests:

- `ecm-frontend/src/pages/RecordsManagementPage.test.tsx`
- `ecm-frontend/src/services/recordsManagementService.test.ts`

## Outcome

After `PR-26D`, RM admins can safely invoke file-plan rename / move from the dedicated RM page while staying on the hardened RM contract. Workspace/system-root move targets remain intentionally deferred.
