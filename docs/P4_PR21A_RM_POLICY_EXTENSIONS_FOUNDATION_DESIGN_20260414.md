# P4 PR-21A RM Policy Extensions Foundation Design

## Scope

`PR-21A` delivers the first low-risk slice of the deferred RM policy-extension backlog.

Delivered scope:

- stricter RM guard on `TrashService.restore(...)`
- new admin telemetry endpoint for RM-governed bulk import and transfer replication activity

Explicitly deferred:

- undeclare / release workflow
- deeper write-path refactors for bulk import and transfer create paths
- dedicated frontend admin page for RM operations telemetry

## Why This Slice

The current deferred backlog had three items:

- undeclare / release
- transfer/import RM operational telemetry
- stricter archive restore / reopen governance

`undeclare` is the highest-risk business semantic and should not be introduced without a product decision. The safest next move is to close the live restore seam and expose operational visibility first.

## Restore Governance

Before this slice, `TrashService.restore(...)` only validated:

- deleted state
- actor ownership / admin rights
- parent trash state

It did not route through RM governance checks. That meant trash restore was the most obvious remaining seam for reintroducing RM-governed content into a writable live state.

`PR-21A` adds a dedicated `RecordsManagementService.assertRestoreAllowed(...)` policy entrypoint and wires `TrashService.restore(...)` through it. The new guard combines:

- `assertArchiveMutationAllowed(...)`
- `assertHierarchyMutationAllowed(...)`

This blocks restore for:

- declared records
- file plans
- nodes inside file-plan scope
- folders containing declared records

## RM Operations Telemetry

This slice adds `GET /api/v1/records/operations` on top of existing job tables.

The endpoint summarizes RM-governed jobs without changing job persistence:

- bulk import jobs are classified by RM governance on the target folder
- transfer replication jobs are classified by RM governance on both source node and target folder

The response includes:

- governed import count
- active governed import count
- governed transfer count
- active governed transfer count
- import status breakdown
- transfer status breakdown
- recent governed import jobs
- recent governed transfer jobs

## Classification Rules

Governance reasons are emitted as stable string codes:

- `SOURCE_DECLARED_RECORD`
- `SOURCE_FILE_PLAN`
- `SOURCE_INSIDE_FILE_PLAN`
- `TARGET_FILE_PLAN`
- `TARGET_INSIDE_FILE_PLAN`

This keeps the API easy to consume from a later admin UI without introducing new enums or migrations.

## Key Files

- `ecm-core/src/main/java/com/ecm/core/service/RecordsManagementService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/RecordsManagementController.java`
- `ecm-core/src/main/java/com/ecm/core/service/TrashService.java`

## Deferred Follow-up

Still intentionally deferred after `PR-21A`:

- undeclare / release
- RM-specific audit events for every transfer/import lifecycle stage
- RM guardrails for bulk import / transfer create paths beyond telemetry
- frontend operations dashboard on top of `/records/operations`
