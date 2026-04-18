# P4 PR-57 RM Activity Contributor Event Type Highlights Design

## Goal

Add a backend-only RM analytics highlights surface for contributor-by-event-type breakdowns, mirroring the recent-window comparison semantics already used by contributor highlights, contributor family highlights, and contributor event-type report.

## Scope

- add `GET /api/v1/records/activity-contributor-event-type-highlights`
- JSON only
- keep the implementation additive and controller/service scoped

Out of scope:

- no frontend page work
- no CSV export
- no new tables
- no new repository queries
- no scheduled materialization
- no change to RM evidence or policy flows

## API Contract

`GET /api/v1/records/activity-contributor-event-type-highlights`

Query params:

- `windowDays`
- `limit`
- `eventTypeLimit`

Range semantics:

- endpoint compares two adjacent closed windows
- current window ends today at `23:59:59`
- previous window is the immediately preceding closed interval of equal duration
- `windowDays` default `7`, clamp `2..30`

Limit semantics:

- contributor `limit` default `5`, clamp `1..50`
- nested event-type `eventTypeLimit` default `3`, clamp `1..10`

## Response Shape

JSON response exposes:

- `windowDays`
- `limit`
- `eventTypeLimit`
- `currentWindow.fromDay`
- `currentWindow.toDay`
- `previousWindow.fromDay`
- `previousWindow.toDay`
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

Contributor rows are built from the union of current-window and previous-window contributors, filtered to rows with non-zero activity in either window.

Nested event-type rows are built from the union of current-window and previous-window exact event types per contributor, filtered to rows with non-zero activity in either window, then top-N constrained by `eventTypeLimit`.

Blank usernames preserve the existing `(System)` contributor semantics already used by RM contributor analytics.

## Implementation

Controller:

- adds the new highlights endpoint
- keeps the response JSON-only, matching the existing highlights surfaces

Service:

- reuses `countRmEventsByUsernameAndTypeBetween(...)` for both current and previous windows
- reuses `aggregateContributorReportContributors(...)` for contributor totals and label normalization
- reuses `aggregateContributorEventTypeReportEventTypes(...)` and `mergeContributorEventTypeReportEventTypes(...)` for nested exact event-type breakdowns
- reuses existing contributor and event-type limit normalization logic instead of introducing a third limit family
- keeps family classification authoritative through `classifyActivityFamily(...)`

Repository:

- no new query

## Files

- [RecordsManagementController.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/RecordsManagementController.java:1)
- [RecordsManagementService.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/RecordsManagementService.java:1)
- [RecordsManagementControllerTest.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/RecordsManagementControllerTest.java:1)
- [RecordsManagementServiceTest.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/RecordsManagementServiceTest.java:1)
