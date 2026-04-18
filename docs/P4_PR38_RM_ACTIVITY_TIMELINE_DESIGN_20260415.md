# P4 PR-38 RM Activity Timeline Design

## Goal

Add a true RM trend surface without introducing new tables by aggregating existing RM audit events into a daily activity timeline.

## Scope

Backend:

- `ecm-core/src/main/java/com/ecm/core/repository/AuditLogRepository.java`
- `ecm-core/src/main/java/com/ecm/core/service/RecordsManagementService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/RecordsManagementController.java`
- `ecm-core/src/test/java/com/ecm/core/service/RecordsManagementServiceTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/RecordsManagementControllerTest.java`

Frontend:

- `ecm-frontend/src/types/index.ts`
- `ecm-frontend/src/services/recordsManagementService.ts`
- `ecm-frontend/src/services/recordsManagementService.test.ts`
- `ecm-frontend/src/pages/RecordsManagementPage.tsx`
- `ecm-frontend/src/pages/RecordsManagementPage.test.tsx`

Documentation:

- `docs/P4_RECORDS_MANAGEMENT_EXECUTION_PLAN_20260414.md`
- `docs/P4_RECORDS_MANAGEMENT_ACCEPTANCE_20260414.md`

## Problem

`PR-37` added a snapshot layer, but the RM admin page still lacked any temporal signal. Admins could see current governance state, yet they could not tell whether declarations, undeclarations, category assignments, or file-plan/category governance changes were accelerating or quiet.

## Design

`PR-38` adds one backend aggregate endpoint and one non-blocking frontend timeline card.

### Backend

New endpoint:

- `GET /api/v1/records/activity-timeline?days=14`

Data source:

- existing `audit_log`
- existing RM audit event families only

Timeline series:

- `declaredCount`
- `undeclaredCount`
- `categoryAssignedCount`
- `governanceChangeCount`

`governanceChangeCount` intentionally aggregates file-plan and record-category lifecycle mutations:

- `RM_FILE_PLAN_*`
- `RM_RECORD_CATEGORY_*`

Excluded from this slice:

- blocked operations such as `RM_RECORD_UNDECLARE_BLOCKED`
- non-RM audit categories
- historical export, CSV, or advanced filtering

### Frontend

The RM admin page adds `RM Activity Timeline`:

- uses the new endpoint
- shows the last `N` days as daily rows
- each row shows:
  - date
  - proportional activity bar
  - total events
  - series detail line

The timeline load is isolated from the rest of the RM page, mirroring the existing governed-operations load isolation.

## Non-Goals

- no new persistence
- no audit export or analytics page
- no trend drilldown by user/event type
- no change to existing RM tables, filters, or operations telemetry contracts

## Risks

- timeline quality depends on RM audit event coverage staying authoritative
- `governanceChangeCount` is intentionally aggregated; if product later wants separate file-plan vs category lines, that should be a follow-up slice

## Result

This slice upgrades the RM admin page from a pure snapshot surface into a lightweight trend dashboard while staying inside current audit infrastructure.
