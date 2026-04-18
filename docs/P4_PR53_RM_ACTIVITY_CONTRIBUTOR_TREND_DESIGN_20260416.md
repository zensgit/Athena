# P4 PR-53 RM Activity Contributor Trend Design

## Goal

Add a backend-only RM analytics trend surface that tracks recent RM activity by contributor across contiguous buckets, aligning the contributor axis with the existing family and event-type trend APIs.

## Scope

- add `GET /api/v1/records/activity-contributor-trend`
- JSON only
- keep the implementation additive and controller/service/repository scoped

Out of scope:

- no frontend page work
- no CSV export
- no new tables
- no scheduled materialization
- no change to RM evidence or policy flows

## API Contract

`GET /api/v1/records/activity-contributor-trend`

Query params:

- `days`
- `bucketDays`
- `limit`

Range semantics:

- window is recent-only, ending today
- `days` default `28`, clamp `7..90`
- `bucketDays` default `7`, clamp `1..14`, then capped at `days`
- `limit` default `5`, clamp `1..20`

## Response Shape

JSON response exposes:

- `days`
- `bucketDays`
- `limit`
- `trackedContributors[]`
- `buckets[]`

Each tracked contributor exposes:

- `username`
- `label`
- `count`
- `lastEventTime`

Each bucket exposes:

- `label`
- `fromDay`
- `toDay`
- `activeDayCount`
- `totalCount`
- `otherCount`
- `contributorCounts[]`

Each bucket contributor row exposes:

- `username`
- `label`
- `count`

Tracked contributors are the current-window top-N contributors by total RM activity. Bucket contributor counts are limited to the tracked set; `otherCount` preserves RM bucket activity outside the tracked contributors.

Blank usernames preserve the existing `(System)` contributor semantics already used by RM contributor analytics.

## Implementation

Controller:

- adds the new trend endpoint
- keeps response JSON-only, matching the existing trend family/event-type endpoints

Service:

- reuses `countRmEventsByUsernameAndTypeBetween(...)` to determine the current-window tracked top contributors
- adds one additive repository query to aggregate daily RM activity by contributor + event type
- builds contiguous buckets using the same timeline/bucketing approach used by family and event-type trend APIs
- computes `otherCount` as the bucket total outside the tracked contributor set

Repository:

- adds `countRmEventsByDayUsernameAndTypeSince(...)`
- no tables or migrations

## Files

- [AuditLogRepository.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/repository/AuditLogRepository.java:1)
- [RecordsManagementController.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/RecordsManagementController.java:1)
- [RecordsManagementService.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/RecordsManagementService.java:1)
- [RecordsManagementControllerTest.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/RecordsManagementControllerTest.java:1)
- [RecordsManagementServiceTest.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/RecordsManagementServiceTest.java:1)
