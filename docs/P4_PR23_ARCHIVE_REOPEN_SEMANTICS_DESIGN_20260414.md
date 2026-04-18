# P4 PR-23 Archive Reopen Semantics Design

## Goal

Define the minimum safe semantics for "archive reopen" after `PR-19`, `PR-21A`, and `PR-22`.

This slice is intentionally design-first. The remaining gap is no longer "can archived content be restored at all", but "what exactly counts as a safe restore once records management, legal hold, and subtree governance are in play".

## Recommendation

Do not introduce a new backend concept named `reopen`.

Use the existing archive restore path as the authoritative repository action:

- backend/domain/service/controller terminology should remain `restore`
- existing API should remain `POST /api/v1/nodes/{nodeId}/restore`
- if product later wants softer UI copy, it can label the action `Reopen` on the archive page only

The repository rule should be:

- archive reopen == restore from archive
- RM hardening should tighten restore preflight instead of adding a second mutation path

## Why This Is Still Needed

Current RM hardening already blocks:

- manual archive of declared records
- manual archive and restore of file plans and file-plan-governed nodes
- trash restore of RM-governed nodes
- archive policy on file plans

The remaining seam is that archive restore should be defined and enforced as a subtree-aware governance action, not just a root-node check.

In current code, `ContentArchiveService.restoreNode(...)` still calls `recordsManagementService.assertArchiveMutationAllowed(node, "restore")`.

For `PR-23`, restore should be hardened so that the full archive scope is preflighted before any node in the scope is mutated.

## Scope

`PR-23` should cover only:

- tightening the existing restore path
- subtree-aware RM preflight for archived folder restore
- explicit blocking semantics for records, file plans, legal holds, and invalid archived states
- optional archive-page copy update if product wants `Reopen`

`PR-23` should not cover:

- a new archive reopen endpoint
- reopen actions in browse/search/document-preview/trash surfaces
- bulk reopen APIs
- non-admin archive UI exposure
- changing the existing restore permission model for non-RM content
- file-plan reopen or release workflow

## Canonical Semantics

Restore means:

- move archived content back to `HOT`
- clear archive metadata on the restored scope
- preserve node identity, path, ACL, content, versions, and parent relationship

Restore does not mean:

- undeclare a record
- release a legal hold
- reopen file-plan-governed content
- bypass RM immutability
- move content out of a file plan

## API Recommendation

Keep the current API unchanged:

- `POST /api/v1/nodes/{nodeId}/restore`

Do not add:

- `POST /api/v1/nodes/{nodeId}/reopen`
- `POST /api/v1/records/.../reopen`

If UI needs softer copy, it should map to the same restore endpoint.

## Permission Boundary

`PR-23` should not widen restore permissions.

Keep the current backend permission model for non-RM content:

- admin
- original archiver
- node owner

RM hardening should only narrow what can be restored, not broaden who can restore it.

Front-end exposure should continue to live only on the existing admin archive page.

## Preconditions

Restore is considered only when:

- target node exists
- target node is `ARCHIVED`
- target node is not soft-deleted
- target node is not in trash
- target node is not a working copy
- target node is not checked out

For folder restore, the same state checks should be applied across the full archive scope before any restore mutation starts.

## Blocking Conditions

Restore must be rejected when any of the following are true for the root or any node in the restore scope:

- an active legal hold intersects the scope
- a declared record exists in the scope
- a file plan exists in the scope
- a node governed by a file plan exists in the scope
- a working copy exists in the scope
- a checked-out document exists in the scope
- a deleted or trashed node exists in the scope

Concrete examples that must be blocked:

- restoring an archived declared record
- restoring an archived folder that contains a declared record
- restoring an archived file plan
- restoring an archived folder that contains a file plan subtree
- restoring an archived folder that contains content governed by a file plan lower in the tree

## Backend Design

Recommended implementation shape:

1. keep `ContentArchiveService.restoreNode(UUID)` as the entry point
2. compute the full restore scope first
3. run a focused preflight over the scope before mutating any node
4. only then flip archive status and clear archive metadata

Recommended helper structure:

- `recordsManagementService.assertRestoreAllowed(node, "restore")` should become the minimum root check
- add a scope-level helper such as `assertRestoreScopeAllowed(List<Node> scope, String operation)` to validate the entire archived subtree

The scope-level check should reuse existing RM helpers where possible, but it must be stricter than the current root-only `assertArchiveMutationAllowed(...)`.

## Expected Backend Change Surface

Primary files likely to change:

- `ecm-core/src/main/java/com/ecm/core/service/ContentArchiveService.java`
- `ecm-core/src/main/java/com/ecm/core/service/RecordsManagementService.java`
- `ecm-core/src/main/java/com/ecm/core/service/LegalHoldService.java`
- `ecm-core/src/test/java/com/ecm/core/service/ContentArchiveServiceTest.java`
- `ecm-core/src/test/java/com/ecm/core/service/RecordsManagementServiceTest.java`

Likely no controller shape change is required.

## Frontend Recommendation

Do not add restore/reopen actions to:

- browse page
- search results
- document preview
- trash page
- RM admin page

If product wants clearer copy, keep it only in `ContentArchivePage`:

- label: `Reopen`
- helper text: `Restore archived content back to HOT storage`

This avoids creating a second mental model outside the archive operator workflow.

## Audit and Activity

`PR-23` does not require a new audit domain.

Keep using the existing restore activity path, but blocked restore responses should remain explicit and actionable:

- legal hold block should say hold name(s)
- RM block should say record/file-plan reason
- subtree block should identify that the folder contains governed content

If later product needs RM-specific restore-block analytics, that should be a separate slice.

## Product Decision

The recommended default is:

- do not invent `reopen` as a new repository action
- harden the current restore path
- keep the UI entry point only on the archive page
- block restore for any RM-governed or hold-governed archived scope

This is the smallest change that closes the remaining governance seam without expanding the product surface.
