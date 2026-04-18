# P4 PR-50 RM Activity Event Type Report Design

## Goal

Add a backend-only RM analytics report/export surface for exact RM event types, mirroring the closed-range comparison semantics already used by the family report.

## Scope

- add `GET /api/v1/records/activity-event-type-report`
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

`GET /api/v1/records/activity-event-type-report`

Query params:

- `from`
- `to`
- `limit`
- `format=json|csv`

Range semantics:

- custom range is a closed interval `[from, to]`
- `from` and `to` must be provided together
- `from` must be before or equal to `to`
- maximum custom span is `90` days
- when both are omitted, the endpoint uses the recent closed `28`-day window from oldest included day at `00:00:00` through today at `23:59:59`
- previous window is the immediately preceding closed interval of equal duration

Limit semantics:

- `limit` default `8`, clamp `1..20`

## Response Shape

JSON response exposes:

- `currentWindow.from`
- `currentWindow.to`
- `previousWindow.from`
- `previousWindow.to`
- `limit`
- `currentTotalCount`
- `previousTotalCount`
- `eventTypes[]`

Each event-type row exposes:

- `eventType`
- `family`
- `currentCount`
- `previousCount`
- `delta`
- `lastEventTime`

The row family is still derived from the authoritative RM family classification:

- `DECLARED`
- `UNDECLARED`
- `CATEGORY_ASSIGNED`
- `GOVERNANCE_CHANGE`
- `OTHER`

CSV export is a flattened one-row-per-event-type rendering of the same report DTO.

## Implementation

Controller:

- branches on `format`
- reuses the same report DTO for JSON and CSV
- returns `text/csv; charset=UTF-8` with attachment headers for CSV

Service:

- reuses `countRmEventsByTypeBetween(...)`
- reuses the same closed-range comparison helper used by the family report
- builds current and previous event-type windows from existing audit aggregates
- keeps `OTHER` handling authoritative through `classifyActivityFamily(...)`

Repository:

- no new query

## Files

- [RecordsManagementController.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/RecordsManagementController.java:1)
- [RecordsManagementService.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/RecordsManagementService.java:1)
- [RecordsManagementControllerTest.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/RecordsManagementControllerTest.java:1)
- [RecordsManagementServiceTest.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/RecordsManagementServiceTest.java:1)
