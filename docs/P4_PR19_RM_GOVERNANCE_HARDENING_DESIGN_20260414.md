# P4 PR-19 RM Governance Hardening Design

## Goal

Close the next Records Management governance seams after `PR-18`:

- block manual archive and restore on RM-governed content
- prevent archive-policy execution from acting on file plans or RM-governed records
- add RM-native audit and reporting endpoints
- close the remaining legal-hold bypass on folder copy

This PR is backend-only. It does not add new RM browse/admin UI.

## Scope

### 1. Manual archive and restore are no longer generic for RM-governed content

`ContentArchiveService` now distinguishes between:

- user-driven archive/restore
- governance/policy archive execution

User-driven archive/restore now rejects:

- declared records
- file plan folders
- nodes inside file-plan scope

This keeps record lifecycle changes behind RM control instead of the generic archive API.

### 2. Legal holds now gate archive policy paths too

`archiveNodeByPolicy(...)` now enforces legal hold checks before archive-state mutation.

That matters because the policy path is used by:

- archive policy execution
- disposition archive execution

Without this, held content could still enter archive state through policy engines even after direct delete/destroy seams were protected.

### 3. Archive policy cannot be attached to file plans

`ArchivePolicyService` now rejects file-plan folders as policy roots.

In addition, candidate selection excludes:

- declared records
- any node governed by a file plan

This keeps archive policy and RM disposition from competing for the same subtree.

### 4. Folder copy now prechecks legal holds

`NodeService.copyNode(...)` now performs legal-hold validation before recursive copy.

That closes the remaining path where held descendants could be duplicated through folder copy even though delete/move/trash were already blocked.

### 5. RM reporting and audit become first-class endpoints

`RecordsManagementService` now exposes:

- `getSummary()`
- `listAudit(...)`

Controller endpoints:

- `GET /api/v1/records/summary`
- `GET /api/v1/records/audit`

The summary is repository-native and focuses on governance state:

- total declared records
- file plan count
- record category count
- uncategorized declared records
- declared records outside file plans
- category/file-plan breakdown buckets

The audit timeline is built on `audit_log` with `RM_%` event types.

### 6. RM mutations now emit dedicated audit events

The following operations now write audit entries:

- `RM_RECORD_DECLARED`
- `RM_RECORD_CATEGORY_ASSIGNED`
- `RM_FILE_PLAN_CREATED`
- `RM_RECORD_CATEGORY_CREATED`

This keeps RM reporting on top of existing audit infrastructure instead of creating a second reporting store.

## Non-Changes

`BulkImportService` and `TransferReceiverService` were audited in this slice but not refactored.

Reason:

- bulk import overwrite already routes through `nodeService.deleteNode(...)`
- transfer overwrite already routes through `nodeService` / `versionService`

Those seams are already covered by the hold/record guards added in earlier PRs plus the new copy precheck.

## Files

- `ecm-core/src/main/java/com/ecm/core/service/RecordsManagementService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/RecordsManagementController.java`
- `ecm-core/src/main/java/com/ecm/core/service/ContentArchiveService.java`
- `ecm-core/src/main/java/com/ecm/core/service/ArchivePolicyService.java`
- `ecm-core/src/main/java/com/ecm/core/service/NodeService.java`
- `ecm-core/src/main/java/com/ecm/core/repository/AuditLogRepository.java`

## Deferred

- undeclare/release workflow
- RM dashboards and browse-page authoring UI
- file-plan tree management UI
- transfer/import specific RM-facing telemetry beyond existing audit events
