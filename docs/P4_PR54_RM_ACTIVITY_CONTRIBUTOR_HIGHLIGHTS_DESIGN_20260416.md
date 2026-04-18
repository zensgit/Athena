# P4 PR-54 RM Activity Contributor Highlights Design

## Goal

Add a backend-only RM analytics highlights surface for contributors, mirroring the existing family highlights semantics with current-vs-previous window comparison.

## Scope

- add `GET /api/v1/records/activity-contributor-highlights`
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

`GET /api/v1/records/activity-contributor-highlights`

Query params:

- `windowDays`
- `limit`

Range semantics:

- endpoint compares two adjacent closed windows
- current window ends today at `23:59:59`
- previous window is the immediately preceding closed interval of equal duration
- `windowDays` default `7`, clamp `2..30`

Limit semantics:

- `limit` default `5`, clamp `1..50`

## Response Shape

JSON response exposes:

- `windowDays`
- `limit`
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

Contributor rows are built from the union of current-window and previous-window contributors, filtered to rows with non-zero activity in either window.

Blank usernames preserve the existing `(System)` contributor semantics already used by RM contributor analytics.

## Implementation

Controller:

- adds the new highlights endpoint
- keeps the response JSON-only, matching the existing family highlights endpoint

Service:

- reuses `countRmEventsByUsernameAndTypeBetween(...)` for both current and previous windows
- reuses `aggregateContributorReportContributors(...)` so username normalization, label generation, totals, and last-event selection stay aligned with the contributor report surface
- sorts contributors by max(current, previous), then current count, then label
- reuses contributor limit constants instead of introducing a second contributor-specific limit model

Repository:

- no new query

## Files

- [RecordsManagementController.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/RecordsManagementController.java:1)
- [RecordsManagementService.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/RecordsManagementService.java:1)
- [RecordsManagementControllerTest.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/RecordsManagementControllerTest.java:1)
- [RecordsManagementServiceTest.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/RecordsManagementServiceTest.java:1)
