# P4 PR-48 RM Activity Family Trend Design

## Goal

Add a backend-only RM analytics trend surface that shows family distribution over contiguous time buckets, without introducing new persistence or a second evidence model.

## Scope

- add `GET /api/v1/records/activity-family-trend`
- aggregate existing `RM_%` audit events into family buckets
- keep the implementation additive and controller/service scoped

Out of scope:

- no frontend work
- no CSV export
- no new tables
- no new repository queries
- no changes to RM policy or evidence flows

## API Contract

`GET /api/v1/records/activity-family-trend`

Query params:

- `days`
- `bucketDays`

Normalization:

- `days` default `28`, clamp `7..90`
- `bucketDays` default `7`, clamp `1..14`
- `bucketDays` is additionally capped to `days`

## Response Shape

- `days`
- `bucketDays`
- `buckets[]`

Each bucket exposes:

- `label`
- `fromDay`
- `toDay`
- `activeDayCount`
- `totalCount`
- `familyCounts[]`

Each family count exposes:

- `family`
- `count`

Family semantics stay aligned with existing RM analytics:

- `DECLARED`
- `UNDECLARED`
- `CATEGORY_ASSIGNED`
- `GOVERNANCE_CHANGE`
- `OTHER`

## Implementation

Controller:

- adds a thin admin-only endpoint on `RecordsManagementController`

Service:

- reuses `countRecordsManagementEventsByDaySince(...)`
- classifies each daily RM event through `classifyActivityFamily(...)`
- groups daily family counts into contiguous buckets using the same remainder-first bucketing approach already used by `activity-breakdown`
- sorts each bucket's `familyCounts` by count desc, then stable family rank

Repository:

- no new query

## Files

- [RecordsManagementController.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/RecordsManagementController.java:1)
- [RecordsManagementService.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/RecordsManagementService.java:1)
- [RecordsManagementControllerTest.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/RecordsManagementControllerTest.java:1)
- [RecordsManagementServiceTest.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/RecordsManagementServiceTest.java:1)
