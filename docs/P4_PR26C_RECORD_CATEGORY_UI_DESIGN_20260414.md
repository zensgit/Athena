# P4 PR-26C Record Category Rename-Move UI Design

## Goal

Expose the now-hardened record-category rename / move backend contract through a thin RM admin UI slice, without reopening generic category mutation seams.

## Scope

`PR-26C` covers:

- service/type support for record-category rename / move
- explicit RM admin dialogs for category rename
- explicit RM admin dialogs for category move
- RM admin table actions wired to the dedicated dialogs
- targeted page/service regression coverage

`PR-26C` does not cover:

- file-plan rename / move UI
- inline freeform table editing for rename / move
- drag-and-drop category tree editing
- generic category manager integration
- search-page or browse-page category rename/move surfaces

## Recommendation

Do not reuse the existing description-edit form for rename / move.

Use explicit dialogs instead:

- `Rename Record Category`
- `Move Record Category`

That keeps the dangerous operations visually separate from the safe description-only edit flow introduced in `PR-25`.

## Why Explicit Dialogs

Record-category rename / move is fan-out behavior, not a local row edit:

- descendant category paths change
- declared-record fallback metadata changes
- affected search documents are reindexed

That deserves dedicated confirmation text instead of silently enabling the previously disabled `name` and `parent` fields in the edit form.

## Frontend Design

### Service Layer

Add:

- `renameRecordCategory(categoryId, { name })`
- `moveRecordCategory(categoryId, { targetParentId })`

These must call the authoritative RM endpoints rather than generic category APIs.

### Page Actions

Extend the existing category row actions on `RecordsManagementPage`:

- `Edit`
- `Rename`
- `Move`
- `Delete`

Root category remains `Protected` and does not expose rename / move actions.

### Rename Dialog

Dialog copy should explain:

- descendant category paths will be repaired
- declared-record metadata will be repaired
- affected search documents will be refreshed

### Move Dialog

Dialog copy should explain the same fan-out semantics and pre-filter obviously illegal targets:

- exclude the current category
- exclude the current parent to avoid same-parent no-op submits
- exclude descendants of the current category
- require the operator to explicitly choose a new RM parent before submit

The backend remains authoritative for cycle protection and any remaining validation.

## Files

Frontend production:

- `ecm-frontend/src/services/recordsManagementService.ts`
- `ecm-frontend/src/types/index.ts`
- `ecm-frontend/src/pages/RecordsManagementPage.tsx`
- `ecm-frontend/src/components/records/RenameRecordCategoryDialog.tsx`
- `ecm-frontend/src/components/records/MoveRecordCategoryDialog.tsx`

Frontend tests:

- `ecm-frontend/src/pages/RecordsManagementPage.test.tsx`
- `ecm-frontend/src/services/recordsManagementService.test.ts`

## Outcome

After `PR-26C`, RM admins can safely invoke category rename / move from the dedicated RM page while still staying on the hardened RM contract.

File-plan rename / move UI remains intentionally deferred as a separate thin slice.
