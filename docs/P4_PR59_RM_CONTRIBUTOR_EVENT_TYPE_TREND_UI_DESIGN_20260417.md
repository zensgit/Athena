# PR-59 RM Contributor Event-Type Trend UI Design

## Scope

- add a thin frontend consumption layer for `GET /api/v1/records/activity-contributor-event-type-trend`
- keep drilldown on the existing `Records Audit` evidence table
- do not change backend contracts
- do not add a second async evidence surface

## Frontend Changes

- add `RecordsActivityContributorEventTypeTrend*` DTOs to frontend types
- add `recordsManagementService.getActivityContributorEventTypeTrend(...)`
- load the trend independently from other RM cards so failure stays isolated
- render a new `RM Contributor Event-Type Trend` card on `RecordsManagementPage`
- each bucket shows:
  - bucket label
  - total events
  - active days
  - outside-tracked-contributors count
  - tracked contributors
  - nested event-type buttons

## Drilldown Contract

- clicking a nested event-type button routes to the existing `Records Audit` section
- drilldown pre-fills:
  - `username`
  - `eventType`
  - `from`
  - `to`
- range uses the bucket `fromDay/toDay` as closed day boundaries

## Out Of Scope

- new charts
- frontend CSV export
- parallel evidence tables
- changes to backend analytics payloads
