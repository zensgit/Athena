# P4 PR-22 Record Undeclare Workflow Acceptance

## Scope

This acceptance document defines the required behavior for a future `PR-22`.

It is planning-only in the current turn. No code implementation is implied by this document.

## Backend Acceptance

- admin can undeclare a declared live document
- undeclare requires a non-empty reason
- undeclare is rejected for non-admin users
- undeclare is rejected when the node is not currently declared as a record
- undeclare is rejected for working copies
- undeclare is rejected for checked-out documents
- undeclare is rejected for deleted, trashed, or archived documents
- undeclare is rejected when an active legal hold applies
- undeclare is rejected when the node is governed by a file plan
- successful undeclare removes the `rm:record` aspect
- successful undeclare removes RM declaration metadata properties
- successful undeclare removes RM record-category binding properties
- successful undeclare does not change content, versions, ACL, parent, or path
- successful undeclare publishes a node-updated lifecycle/index event
- successful undeclare writes `RM_RECORD_UNDECLARED` audit
- blocked undeclare writes `RM_RECORD_UNDECLARE_BLOCKED` audit
- `GET /api/v1/nodes/{nodeId}/record` returns `404` after successful undeclare
- full backend regression remains green

## Frontend Acceptance

- admin sees `Undeclare Record...` only for declared records
- non-admin users do not see undeclare actions
- undeclare action is available from document preview
- undeclare action is available from the RM admin declared-records table
- undeclare dialog requires a reason before submit
- successful undeclare refreshes the current record state
- successful undeclare removes the record badge/details from preview after refresh
- blocked undeclare shows an actionable error message
- full front-end regression remains green
- front-end production build succeeds

## Out Of Scope

- approval workflow for undeclare
- bulk undeclare
- undeclare of folders
- file-plan release workflow
- legal-hold release workflow
- disposition-history rewrite
