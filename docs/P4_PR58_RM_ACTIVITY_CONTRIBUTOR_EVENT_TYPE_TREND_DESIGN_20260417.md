# PR-58 RM Activity Contributor Event-Type Trend Design

## Scope

- add backend-only `GET /api/v1/records/activity-contributor-event-type-trend`
- keep source data on existing `RM_%` audit events
- keep implementation on existing daily contributor + event-type aggregation
- do not add new tables, migrations, or repository queries
- do not add frontend work

## Endpoint

- path: `/api/v1/records/activity-contributor-event-type-trend`
- params:
  - `days`
  - `bucketDays`
  - `limit`
  - `eventTypeLimit`

## Response

- top-level:
  - `days`
  - `bucketDays`
  - `limit`
  - `eventTypeLimit`
  - `trackedContributors[]`
  - `buckets[]`
- each bucket:
  - `label`
  - `fromDay`
  - `toDay`
  - `activeDayCount`
  - `totalCount`
  - `otherCount`
  - `contributorCounts[]`
- each contributor row:
  - `username`
  - `label`
  - `count`
  - `eventTypes[]`
- each nested event-type row:
  - `eventType`
  - `family`
  - `count`

## Design Notes

- tracked contributors are derived from the same full-window top contributor path used by `activity-contributor-trend`
- bucket aggregation reuses `countRmEventsByDayUsernameAndTypeSince(...)`
- `otherCount` remains activity outside tracked contributors
- nested event types are scoped per tracked contributor inside each bucket
- nested event types are sorted by count desc, then family, then event type
- blank usernames still surface as `(System)` through the existing contributor label path

## Out Of Scope

- CSV export
- new audit evidence surface
- frontend chart/card work
- new repository queries or materialized tables
