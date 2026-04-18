# P4 PR-40 RM Activity Breakdown Design

## Goal

Add a bucketed RM trend surface that summarizes recent RM activity into contiguous time windows, so operators can read medium-term trend shape without manually scanning every daily point.

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

`PR-38` gave the RM page raw daily trend data and `PR-39` gave it current-vs-previous-window comparison, but the page still lacked a medium-grain trend surface.

Daily rows are good for audit-like inspection. Window summaries are good for comparison. What was still missing was a chart-ready, bucketed view of recent RM activity that answers: "How did this month break down week by week?"

## Design

`PR-40` adds one backend bucket-aggregation endpoint and one non-blocking frontend breakdown card.

### Backend

New endpoint:

- `GET /api/v1/records/activity-breakdown?days=28&bucketDays=7`

Data source:

- existing `audit_log`
- existing RM audit event families only
- existing daily aggregation path reused from `PR-38` / `PR-39`

Response shape:

- `days`
- `bucketDays`
- `buckets`

Bucket fields:

- `label`
- `fromDay`
- `toDay`
- `activeDayCount`
- `declaredCount`
- `undeclaredCount`
- `categoryAssignedCount`
- `governanceChangeCount`
- `totalCount`

Bucketing rule:

- aggregation remains chronological
- the most recent bucket should remain full whenever possible
- any remainder window is placed at the oldest side

This keeps the current operator-facing bucket semantically aligned with the latest recent window.

### Frontend

The RM admin page adds `RM Activity Breakdown`:

- loads independently from summary, telemetry, highlights, and timeline
- shows recent RM activity grouped into `bucketDays` buckets
- each bucket shows:
  - bucket label
  - proportional stacked bar
  - total events
  - series detail line

The breakdown card is meant to sit between highlights and timeline:

- highlights answer "up or down vs previous window?"
- breakdown answers "how did the recent period break down by window?"
- timeline answers "what happened day by day?"

## Non-Goals

- no new persistence
- no chart library
- no CSV/export
- no drilldown from bucket to filtered audit events

## Result

This slice upgrades the RM analytics surface from "daily + summary" to "summary + medium-grain buckets + daily timeline" while staying on the current audit-log contract.
