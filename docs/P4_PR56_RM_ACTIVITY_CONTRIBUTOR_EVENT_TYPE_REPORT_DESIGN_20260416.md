# P4 PR-56 RM Activity Contributor Event Type Report Design

## Goal

Add a backend-only RM analytics report/export surface for contributor-by-event-type breakdowns, filling the gap between contributor report totals and exact event-type report totals.

## Scope

- add `GET /api/v1/records/activity-contributor-event-type-report`
- default response: JSON
- optional `format=csv` export on the same endpoint
- keep the implementation additive and controller/service scoped

Out of scope:

- no frontend page work
- no new tables
- no new repository queries
- no scheduled materialization
- no change to RM evidence or policy flows

## API Contract

`GET /api/v1/records/activity-contributor-event-type-report`

Query params:

- `from`
- `to`
- `limit`
- `eventTypeLimit`
- `format=json|csv`

Range semantics:

- custom range is a closed interval `[from, to]`
- `from` and `to` must be provided together
- `from` must be before or equal to `to`
- maximum custom span is `90` days
- when both are omitted, the endpoint uses the recent closed `28`-day window from oldest included day at `00:00:00` through today at `23:59:59`
- previous window is the immediately preceding closed interval of equal duration

Limit semantics:

- contributor `limit` default `5`, clamp `1..50`
- nested event-type `eventTypeLimit` default `3`, clamp `1..10`

## Response Shape

JSON response exposes:

- `currentWindow.from`
- `currentWindow.to`
- `previousWindow.from`
- `previousWindow.to`
- `limit`
- `eventTypeLimit`
- `currentTotalCount`
- `previousTotalCount`
- `contributors[]`

Each contributor row exposes:

- `username`
- `label`
- `currentCount`
- `previousCount`
- `delta`
- `lastEventTime`
- `eventTypes[]`

Each nested event-type row exposes:

- `eventType`
- `family`
- `currentCount`
- `previousCount`
- `delta`
- `lastEventTime`

Contributor totals are full-window totals over all contributors in the current and previous ranges. The contributor row list remains top-N constrained. Nested event types are merged from current and previous windows per contributor and then top-N constrained by the configured `eventTypeLimit`.

Blank usernames preserve the existing `(System)` contributor semantics already used by RM contributor analytics.

CSV export is a flattened one-row-per-contributor-event-type rendering of the same report DTO.

## Implementation

Controller:

- branches on `format`
- reuses the same report DTO for JSON and CSV
- returns `text/csv; charset=UTF-8` with attachment headers for CSV

Service:

- reuses `countRmEventsByUsernameAndTypeBetween(...)`
- reuses the same closed-range comparison helper used by contributor/family/event-type reports
- reuses `aggregateContributorReportContributors(...)` for contributor totals and label normalization
- adds an additive in-memory aggregator for per-contributor exact event-type counts
- classifies nested event types through `classifyActivityFamily(...)` so family naming stays authoritative

Repository:

- no new query

## Files

- [RecordsManagementController.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/RecordsManagementController.java:1)
- [RecordsManagementService.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/RecordsManagementService.java:1)
- [RecordsManagementControllerTest.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/RecordsManagementControllerTest.java:1)
- [RecordsManagementServiceTest.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/RecordsManagementServiceTest.java:1)
