# P4 PR-16 Record Declaration Foundation Design

## Goal

Add the smallest safe Records Management slice after `Legal Hold` and `Disposition Schedule`:

- declare a live document as a record
- persist declaration state in existing node/aspect storage
- block user-driven mutations that would alter or remove declared records

This PR is backend-only. It does not introduce file plans, retention categories, undeclare flows, or frontend authoring.

## Storage Model

The declaration is stored on the existing node model instead of a new RM aggregate table:

- aspect: `rm:record`
- properties:
  - `rm:declaredAt`
  - `rm:declaredBy`
  - `rm:declarationComment`
  - `rm:declaredVersionLabel`

This keeps the first slice aligned with Athena's existing `node_aspects + nodes.properties` design and avoids a second governance store before declaration semantics are stable.

## Schema

`080-seed-record-management-aspect.xml` seeds a lightweight content model entry:

- model prefix: `rm`
- aspect: `rm:record`
- aspect properties for declaration metadata

The aspect is modeled so the dictionary can discover it later, but the runtime declaration path still goes through a dedicated service instead of the generic aspect API.

## Service Surface

New backend service:

- `RecordsManagementService`
  - `listRecords()`
  - `getRecord(nodeId)`
  - `declareRecord(nodeId, request)`
  - `assertDirectMutationAllowed(node, operation)`
  - `assertHierarchyMutationAllowed(node, operation)`

New controller:

- `GET /api/v1/records`
- `GET /api/v1/nodes/{nodeId}/record`
- `PUT /api/v1/nodes/{nodeId}/record`

All endpoints are admin-only, matching current governance surfaces such as legal holds and disposition schedules.

## Guard Strategy

Two guard types are used:

- direct guard: blocks mutation of a declared document itself
- hierarchy guard: blocks structural/destructive operations on folders that contain declared records

Applied seams:

- `NodeService.updateNode()`
  - direct block for declared documents
  - rename block for folders containing declared descendants
- `NodeService.moveNode()`
- `NodeService.copyNode()`
- `NodeService.deleteNode()`
- `NodeService.checkoutDocument()`
- `NodeService.addAspect()/removeAspect()`
  - generic `rm:record` add is rejected so declaration metadata always comes from the dedicated RM service
- `TrashService.moveToTrash()/permanentDelete()/emptyTrash()/purgeOldTrashItems()`
- `CheckOutCheckInService.checkout()`
- `VersionService.createVersion()/revertToVersion()/deleteVersion()`

Not blocked in this slice:

- governance destroy via `deleteNodeByGovernance()`
- archive/disposition execution paths

That is intentional. Declared records remain governed by the existing `Legal Hold + Disposition` control layer rather than becoming undeletable forever.

## Out Of Scope

- file plan hierarchy
- record categories / classification UI
- undeclare / release record workflow
- transfer/archive import reconciliation for declared records
- frontend browse/search badges

## Key Files

- `ecm-core/src/main/resources/db/changelog/changes/080-seed-record-management-aspect.xml`
- `ecm-core/src/main/java/com/ecm/core/service/RecordsManagementService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/RecordsManagementController.java`
- `ecm-core/src/main/java/com/ecm/core/service/NodeService.java`
- `ecm-core/src/main/java/com/ecm/core/service/TrashService.java`
- `ecm-core/src/main/java/com/ecm/core/service/CheckOutCheckInService.java`
- `ecm-core/src/main/java/com/ecm/core/service/VersionService.java`

