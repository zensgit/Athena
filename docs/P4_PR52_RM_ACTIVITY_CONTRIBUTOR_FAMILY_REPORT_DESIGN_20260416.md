# P4 PR-52 RM Activity Contributor Family Report Design

## Goal

Add a backend-only RM analytics report/export surface that pivots the existing RM audit comparison model onto contributor-family breakdowns.

## Scope

- add `GET /api/v1/records/activity-contributor-family-report`
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

`GET /api/v1/records/activity-contributor-family-report`

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

- contributor `limit` default `5`, clamp `1..50`

## Response Shape

JSON response exposes:

- `currentWindow.from`
- `currentWindow.to`
- `previousWindow.from`
- `previousWindow.to`
- `limit`
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
- `families[]`

Each nested family row exposes:

- `family`
- `currentCount`
- `previousCount`
- `delta`
- `lastEventTime`

Contributor totals remain full-window totals over all contributors in the current and previous ranges. Contributor rows stay top-N constrained.

Blank usernames preserve the existing `(System)` contributor semantics already used by RM contributor analytics.

Family classification remains authoritative through the existing RM family model:

- `DECLARED`
- `UNDECLARED`
- `CATEGORY_ASSIGNED`
- `GOVERNANCE_CHANGE`
- `OTHER`

CSV export is a flattened one-row-per-contributor-family rendering of the same report DTO.

## Implementation

Controller:

- branches on `format`
- reuses the same report DTO for JSON and CSV
- returns `text/csv; charset=UTF-8` with attachment headers for CSV

Service:

- reuses `countRmEventsByUsernameAndTypeBetween(...)`
- reuses the same closed-range comparison helper used by the family report, event-type report, and contributor report
- builds current and previous contributor windows from existing audit aggregates
- derives family buckets from the authoritative `classifyActivityFamily(...)` seam
- computes total counts over the full contributor windows before top-N row limiting

Repository:

- no new query

## Files

- [RecordsManagementController.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/RecordsManagementController.java:1)
- [RecordsManagementService.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/RecordsManagementService.java:1)
- [RecordsManagementControllerTest.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/RecordsManagementControllerTest.java:1)
- [RecordsManagementServiceTest.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/RecordsManagementServiceTest.java:1)
