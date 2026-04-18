# P4 PR-22 Record Undeclare Workflow Design

## Goal

Define the minimum safe `undeclare` workflow for records management without implementing code yet.

This slice is intentionally design-first because undeclare is not a generic convenience action. It changes governance state and must not weaken the guarantees already added for legal hold, disposition, file plans, audit, or immutable declared records.

## Recommendation

Implement `undeclare` only as a narrow administrative exception workflow.

Do not model it as a generic "release" operation in backend code or API names:

- backend/domain/service/controller/audit terminology should use `undeclare`
- UI may use softer copy later if product wants it, but the authoritative repository term should remain `undeclare`
- this avoids confusion with legal-hold release semantics

## Scope

`PR-22` should cover only:

- admin-only undeclare of a declared record
- strict preflight validation
- mandatory audit trail
- minimal admin UI entry points

`PR-22` should not cover:

- approval workflow
- owner/self-service undeclare
- bulk undeclare
- file-plan release
- undeclare of folders
- retroactive mutation of versions, binaries, ACLs, paths, or disposition history

## Canonical Semantics

Undeclare means:

- remove the `rm:record` governance state from a single live document
- remove record-declaration metadata owned by RM
- preserve content, version chain, node identity, ACL, parent, path, and audit history

Undeclare does not mean:

- unlock content for arbitrary historical rewrite
- release a legal hold
- move a node out of file-plan governance
- delete or rewrite disposition history

## API Recommendation

Use an explicit action endpoint instead of overloading `DELETE /record`:

- `POST /api/v1/nodes/{nodeId}/record/undeclare`

Request body:

```json
{
  "reason": "Administrative correction"
}
```

Response:

- `200 OK` with the updated node declaration state omitted, or
- `204 No Content`

I recommend `204 No Content` and using `GET /api/v1/nodes/{nodeId}/record` returning `404` as the post-condition check.

## Permission Boundary

Keep `undeclare` aligned with the current RM authority model:

- only `ROLE_ADMIN`

Do not open this to:

- document owner
- site manager
- record creator
- file-plan manager

If the product later wants delegated undeclare, that should be a separate approval workflow, not part of `PR-22`.

## Preconditions

Undeclare is allowed only when all of the following are true:

- target node exists
- target node is a `Document`
- node is `LIVE`
- node is not soft-deleted and not in trash
- node is not archived
- node is not a working copy
- node is not checked out
- node currently has `rm:record`

## Blocking Conditions

Undeclare must be rejected when any of the following are true:

- node is under an active legal hold
- node is governed by a file plan
- node is checked out
- node is a working copy
- node is deleted, trashed, or archived

Future-proof rule:

- if Athena later adds disposition execution directly against non-file-plan records, any existing disposition execution history should also block undeclare

## Mutation Semantics

On success, `undeclare` should:

- remove `rm:record`
- remove RM declaration properties:
  - `rm:declaredAt`
  - `rm:declaredBy`
  - `rm:declarationComment`
  - `rm:declaredVersionLabel`
  - `rm:recordCategoryId`
  - `rm:recordCategoryName`
  - `rm:recordCategoryPath`
- update `lastModifiedBy`
- update `lastModifiedDate`
- publish node-updated lifecycle/index refresh

On success, `undeclare` must not:

- change `contentId`
- create/delete versions
- rewrite binary references
- change permissions
- move the node
- change `path`
- remove historical audit events

## Audit Requirements

Minimum events for `PR-22`:

- `RM_RECORD_UNDECLARED`
- `RM_RECORD_UNDECLARE_BLOCKED`

Event detail should include:

- node id
- node name
- current user
- undeclare reason
- blocking reason when rejected

Do not reuse legal-hold event names.

## Backend Change Surface

Primary files likely to change:

- `ecm-core/src/main/java/com/ecm/core/service/RecordsManagementService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/RecordsManagementController.java`
- `ecm-core/src/test/java/com/ecm/core/service/RecordsManagementServiceTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/RecordsManagementControllerTest.java`

Service additions:

- `UndeclareRecordRequest`
- `undeclareRecord(UUID nodeId, UndeclareRecordRequest request)`
- focused preflight helpers for:
  - legal hold
  - file-plan governance
  - working-copy / checked-out state
  - live/archive/trash state

## Frontend Change Surface

Recommended minimal UI:

- add `Undeclare Record...` action to `DocumentPreview` for `admin + declared record`
- add row-level undeclare action in `RecordsManagementPage` declared-records table
- add a small confirmation dialog with required `reason`

Primary files likely to change:

- `ecm-frontend/src/components/preview/DocumentPreview.tsx`
- `ecm-frontend/src/pages/RecordsManagementPage.tsx`
- `ecm-frontend/src/services/recordsManagementService.ts`
- `ecm-frontend/src/types/index.ts`

## Product Decision

The recommended default is:

- implement undeclare
- require admin
- require non-empty reason
- block undeclare under legal hold and file-plan governance

If business stakeholders do not want undeclare at all, the alternative is also valid:

- explicitly document "declared records are irreversible"

But if undeclare exists, it should exist only with the restrictions above.
