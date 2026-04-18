# PR-43 RM Contributor Family Drilldown Design

## Goal

Let RM operators drill from contributor family counters into the existing `Records Audit` evidence table, without creating a second audit surface.

## Scope

Backend:

- extend `GET /api/v1/records/audit` with optional `family`
- keep the existing endpoint and pagination model
- reuse the existing RM event-family classification

Frontend:

- add `Family` to the existing `Records Audit` filters
- make contributor family counters clickable
- keep drilldown on the same audit banner + table path used by `PR-41`

## Backend Contract

`GET /api/v1/records/audit` now accepts optional:

- `family=DECLARED`
- `family=UNDECLARED`
- `family=CATEGORY_ASSIGNED`
- `family=GOVERNANCE_CHANGE`

Existing filters remain unchanged:

- `eventType`
- `username`
- `from`
- `to`
- `page`
- `size`

Invalid `family` values are rejected during enum binding and returned as `400` via the existing `MethodArgumentTypeMismatchException` handler.

## Semantics

`family` and `eventType` use `AND` semantics:

- no `family`: preserve the old `RM_%` query path
- `family` only: use the resolved family event set
- `family + matching eventType`: narrow to that single event type
- `family + conflicting eventType`: return an empty page and do not hit the repository

## Family Mapping

- `DECLARED`
  - `RM_RECORD_DECLARED`
- `UNDECLARED`
  - `RM_RECORD_UNDECLARED`
- `CATEGORY_ASSIGNED`
  - `RM_RECORD_CATEGORY_ASSIGNED`
- `GOVERNANCE_CHANGE`
  - governance events already listed in `RM_TIMELINE_GOVERNANCE_EVENTS`

This keeps contributor drilldown aligned with:

- timeline
- highlights
- breakdown
- contributor aggregation

Out-of-family RM events such as `RM_RECORD_UNDECLARE_BLOCKED` remain intentionally excluded, matching the existing analytics model.

## Frontend Flow

### Audit Filter

The `Records Audit` section adds a `Family` select with:

- `All Families`
- `Declared`
- `Undeclared`
- `Category Assigned`
- `Governance Changes`

This filter is carried by the existing `auditFilters` state and sent through the existing `recordsManagementService.listAudit(...)` call.

### Contributor Family Drilldown

Each contributor row exposes four family-level actions:

- `Declared N`
- `Undeclared N`
- `Category Assigned N`
- `Governance Changes N`

Clicking one of them:

- keeps the contributor window semantics from `PR-42`
- pre-fills:
  - `family`
  - `username`
  - `from`
  - `to`
- reuses the existing audit banner and audit table

## Files

Backend:

- `ecm-core/src/main/java/com/ecm/core/controller/RecordsManagementController.java`
- `ecm-core/src/main/java/com/ecm/core/service/RecordsManagementService.java`
- `ecm-core/src/main/java/com/ecm/core/repository/AuditLogRepository.java`

Frontend:

- `ecm-frontend/src/services/recordsManagementService.ts`
- `ecm-frontend/src/pages/RecordsManagementPage.tsx`

Tests:

- `ecm-core/src/test/java/com/ecm/core/controller/RecordsManagementControllerTest.java`
- `ecm-core/src/test/java/com/ecm/core/service/RecordsManagementServiceTest.java`
- `ecm-frontend/src/services/recordsManagementService.test.ts`
- `ecm-frontend/src/pages/RecordsManagementPage.test.tsx`

## Deferred

- case-insensitive `family` parsing
- new audit endpoint or second evidence surface
- reclassifying blocked RM audit events into an activity family
