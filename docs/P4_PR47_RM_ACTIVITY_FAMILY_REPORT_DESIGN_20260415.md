# P4 PR-47 RM Activity Family Report Design

## Goal

Add a backend-only RM analytics report/export surface on top of existing `RM_%` audit aggregates.

## Scope

- add `GET /api/v1/records/activity-family-report`
- default response: JSON
- optional `format=csv` export on the same endpoint
- keep the implementation additive and controller/service scoped

Out of scope:

- no new tables
- no new async export task
- no frontend page changes
- no repository-kernel or policy changes

## API Contract

`GET /api/v1/records/activity-family-report`

Query params:

- `from`
- `to`
- `eventTypeLimit`
- `contributorLimit`
- `format=json|csv`

Range semantics:

- custom range is a closed interval `[from, to]`
- `from` and `to` must be provided together
- `from` must be before or equal to `to`
- maximum custom span is `90` days
- when both are omitted, the endpoint uses the recent closed `28`-day window from oldest included day at `00:00:00` through today at `23:59:59`
- previous window is derived as the immediately preceding closed interval of equal duration

Limit semantics:

- `eventTypeLimit` default `3`, clamp `1..10`
- `contributorLimit` default `3`, clamp `1..10`

## Response Shape

JSON response exposes:

- `currentWindow.from`
- `currentWindow.to`
- `previousWindow.from`
- `previousWindow.to`
- `eventTypeLimit`
- `contributorLimit`
- `currentTotalCount`
- `previousTotalCount`
- `families[]`

Each family row exposes:

- `family`
- `currentCount`
- `previousCount`
- `delta`
- `lastEventTime`
- `topEventTypes[]`
- `topContributors[]`

Family classification stays aligned with existing RM analytics:

- `DECLARED`
- `UNDECLARED`
- `CATEGORY_ASSIGNED`
- `GOVERNANCE_CHANGE`
- `OTHER`

CSV export is a flattened one-row-per-family rendering of the same report DTO.

## Implementation

Controller:

- branches on `format`
- reuses the same service DTO for JSON and CSV
- returns `text/csv; charset=UTF-8` with `attachment` headers for CSV

Service:

- reuses `aggregateActivityFamilies(...)` for current/previous totals
- reuses `countRmEventsByTypeBetween(...)` for per-family top event types
- reuses `countRmEventsByUsernameAndTypeBetween(...)` for per-family top contributors
- reuses `classifyActivityFamily(...)` so report family semantics stay consistent with audit drilldown, family mix, and family highlights

Repository:

- no new query added

## Files

- [RecordsManagementController.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/RecordsManagementController.java:1)
- [RecordsManagementService.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/RecordsManagementService.java:1)
- [RecordsManagementControllerTest.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/RecordsManagementControllerTest.java:1)
- [RecordsManagementServiceTest.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/RecordsManagementServiceTest.java:1)
