# P4 PR-62 RM Activity Contributor Family Trend Design

## Scope

`PR-62` adds a backend-only RM analytics endpoint:

- `GET /api/v1/records/activity-contributor-family-trend`

This continues the contributor analytics line by filling the missing `trend` slice for contributor-family analytics, so the contributor/family axis now has:

- highlights
- report/export
- trend

## Goals

- bucket recent RM audit activity by contributor
- keep nested breakdown at RM family level
- preserve tracked-contributor vs other semantics already used by contributor trend endpoints
- avoid introducing new tables, migrations, or repository queries

## API

### Request

`GET /api/v1/records/activity-contributor-family-trend`

Query params:

- `days` optional
- `bucketDays` optional
- `limit` optional

### Response

- `days`
- `bucketDays`
- `limit`
- `trackedContributors[]`
- `buckets[]`

Each bucket exposes:

- `label`
- `fromDay`
- `toDay`
- `activeDayCount`
- `totalCount`
- `otherCount`
- `contributorCounts[]`

Each contributor row exposes:

- `username`
- `label`
- `count`
- `families[]`

Each nested family row exposes:

- `family`
- `count`

## Design Notes

- reuses the existing contributor top-N path built from `countRmEventsByUsernameAndTypeBetween(...)`
- reuses the existing daily contributor/event source `countRmEventsByDayUsernameAndTypeSince(...)`
- classifies each event type through the existing RM family classifier
- sorts nested families by:
  - count desc
  - RM family rank
  - family name
- preserves `(System)` handling through the existing contributor key/label helpers

## Non-Goals

- no frontend change
- no CSV/report export surface in this slice
- no new evidence table
- no new database query
- no change to existing contributor report/highlights/trend contracts
