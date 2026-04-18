# P4 PR-49 RM Activity Event Type Trend Design

## Goal

Add a backend-only RM analytics trend surface for exact RM event types, so later UI/chart work can read a stable bucketed event-type series without re-deriving it client-side.

## Scope

- add `GET /api/v1/records/activity-event-type-trend`
- expose recent bucketed RM event-type trend data
- keep the implementation additive and controller/service scoped

Out of scope:

- no frontend changes
- no CSV export
- no new tables
- no new repository queries
- no changes to RM evidence or policy flows

## API Contract

`GET /api/v1/records/activity-event-type-trend`

Query params:

- `days`
- `bucketDays`
- `limit`

Normalization:

- `days` default `28`, clamp `7..90`
- `bucketDays` default `7`, clamp `1..14`
- `bucketDays` is capped to `days`
- `limit` default `8`, clamp `1..20`

## Response Shape

- `days`
- `bucketDays`
- `limit`
- `trackedEventTypes[]`
- `buckets[]`

Tracked event types expose:

- `eventType`
- `family`
- `count`
- `lastEventTime`

Each bucket exposes:

- `label`
- `fromDay`
- `toDay`
- `activeDayCount`
- `totalCount`
- `otherCount`
- `eventTypeCounts[]`

Each event-type count exposes:

- `eventType`
- `family`
- `count`

## Semantics

- tracked event types are the current window top-N exact RM event types, using the same family classification as existing RM analytics
- bucket event counts are limited to the tracked top-N event types so future consumers can render stable series
- `otherCount` captures the remainder of RM activity in the bucket outside the tracked top-N set

## Implementation

Controller:

- adds a thin admin-only endpoint on `RecordsManagementController`

Service:

- reuses `countRmEventsByTypeBetween(...)` to pick tracked top-N event types
- reuses `countRecordsManagementEventsByDaySince(...)` to build bucketed trend data
- uses the same remainder-first contiguous bucketing approach as RM family trend / activity breakdown
- keeps family classification authoritative through `classifyActivityFamily(...)`

Repository:

- no new query

## Files

- [RecordsManagementController.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/RecordsManagementController.java:1)
- [RecordsManagementService.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/RecordsManagementService.java:1)
- [RecordsManagementControllerTest.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/RecordsManagementControllerTest.java:1)
- [RecordsManagementServiceTest.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/RecordsManagementServiceTest.java:1)
