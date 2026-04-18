# PR-60 RM Contributor Event-Type Highlights UI Design

## Scope

- add a thin frontend consumption layer for `GET /api/v1/records/activity-contributor-event-type-highlights`
- keep drilldown on the existing `Records Audit` evidence table
- do not change backend contracts
- do not add a second async evidence surface

## Frontend Changes

- add `RecordsActivityContributorEventTypeHighlights*` DTOs to frontend types
- add `recordsManagementService.getActivityContributorEventTypeHighlights(...)`
- load the highlights independently from other RM cards so failure stays isolated
- render a new `RM Contributor Event-Type Highlights` card on `RecordsManagementPage`
- each contributor row shows:
  - current / previous totals
  - signed delta
  - last event time
  - nested exact event-type rows
- each nested event-type row shows:
  - current / previous counts
  - family
  - signed delta
  - current / previous audit drilldown actions

## Drilldown Contract

- clicking `Review current audit` or `Review previous audit` routes to the existing `Records Audit` section
- drilldown pre-fills:
  - `username`
  - `eventType`
  - `from`
  - `to`
- range uses the selected current or previous highlight window as closed day boundaries

## Out Of Scope

- new charts
- frontend CSV export
- parallel evidence tables
- changes to backend analytics payloads
