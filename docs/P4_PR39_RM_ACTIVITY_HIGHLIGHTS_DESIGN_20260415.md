# P4 PR-39 RM Activity Highlights Design

## Goal

Add a reusable RM trend-summary surface that compares the current RM activity window with the previous window, without introducing new persistence or a separate analytics subsystem.

## Scope

Backend:

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

`PR-38` added a daily RM activity timeline, but the RM admin page still lacked a compact answer to the operator question: "Is RM activity rising or falling compared with the previous window?"

The timeline is useful for raw inspection, but it still forces admins to manually compare days. A thin comparison slice is enough to make the page feel analytical without adding charts or a separate metrics store.

## Design

`PR-39` adds one backend comparison endpoint and one non-blocking highlights card.

### Backend

New endpoint:

- `GET /api/v1/records/activity-highlights?windowDays=7`

Data source:

- existing `audit_log`
- existing RM audit event families only
- existing daily aggregation path reused from `PR-38`

Response shape:

- `windowDays`
- `currentWindow`
- `previousWindow`
- `busiestDay`

Window fields:

- `fromDay`
- `toDay`
- `activeDayCount`
- `declaredCount`
- `undeclaredCount`
- `categoryAssignedCount`
- `governanceChangeCount`
- `totalCount`

`busiestDay` is the highest-total RM activity day across the compared windows.

This slice intentionally stays small:

- no new tables
- no historical export
- no percentages persisted by the API
- no chart-specific shape

### Frontend

The RM admin page adds `RM Activity Highlights`:

- loads independently from summary, telemetry, and timeline
- shows the current and previous window ranges
- shows current-window totals with simple previous-window deltas
- shows the busiest recent RM day

The highlights card is intentionally summary-first. The operator can use it as a compact comparison layer, then drop down into the existing `RM Activity Timeline` if more temporal detail is needed.

## Non-Goals

- no new chart library
- no separate analytics route
- no user/event-type breakdowns
- no replacement of the existing timeline card

## Result

This slice upgrades the RM admin page from "snapshot + raw timeline" to "snapshot + comparison summary + raw timeline" while staying on top of the existing audit-log contract.
