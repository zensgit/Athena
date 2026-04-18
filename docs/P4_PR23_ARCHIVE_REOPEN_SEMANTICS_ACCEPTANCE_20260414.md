# P4 PR-23 Archive Reopen Semantics Acceptance

## Scope

This acceptance document defines the required behavior for a future `PR-23`.

It is planning-only in the current turn. No code implementation is implied by this document.

## Backend Acceptance

- restore continues to use `POST /api/v1/nodes/{nodeId}/restore`
- no new `reopen` backend endpoint is introduced
- restore of a non-archived node is rejected
- restore of a deleted or trashed archived node is rejected
- restore of an archived working copy is rejected
- restore of an archived checked-out document is rejected
- restore is rejected when an active legal hold intersects the restore scope
- restore is rejected for an archived declared record
- restore is rejected for an archived folder containing declared records
- restore is rejected for an archived file plan
- restore is rejected for an archived node governed by a file plan
- restore is rejected for an archived folder containing a file plan subtree
- restore is rejected for an archived folder containing nodes governed by a file plan lower in the tree
- restore preflight validates the full archive scope before any node is mutated
- successful restore continues to clear archive metadata and move the full scope back to `HOT`
- successful restore does not change content, versions, ACL, parent, or path
- successful restore preserves existing restore permission semantics for non-RM content
- full backend regression remains green

## Frontend Acceptance

- archive restore/reopen action remains available only from the archive admin page
- browse page does not gain a reopen action
- document preview does not gain a reopen action
- trash page does not gain a reopen action
- search results do not gain a reopen action
- if UI copy is changed to `Reopen`, it still calls the existing restore API
- blocked restore surfaces a clear RM or legal-hold error
- full front-end regression remains green if front-end copy changes are made
- front-end production build succeeds if front-end copy changes are made

## Out Of Scope

- new `reopen` API or controller surface
- new restore permission model
- reopen action outside the archive operator workflow
- bulk archive reopen APIs
- file-plan release workflow
- undeclare workflow changes
