# P5 PR-92: RM Report Preset Execute Endpoint Design

## Scope

Add the minimum backend execution route for saved RM report presets:

- `POST /api/v1/records/report-presets/{presetId}/execute`
- owner-scoped via existing preset ownership checks
- no new table, no migration, no scheduler
- no second report engine inside the preset layer

The endpoint expands the stored preset params and dispatches to the existing
Records Management report methods.

## Design

### Route placement

The execute route lives in `RecordsManagementController`, not in a new
specialized controller layer. This keeps the implementation thin because the
controller already owns:

- the existing RM report endpoints
- `json|csv` format normalization
- CSV builders for the existing report DTOs

`RmReportPresetService` remains the authoritative owner/read gate for the
preset itself.

### Execution flow

1. Read owned preset through `RmReportPresetService.getOwned(...)`
2. Normalize `format=json|csv`
3. Dispatch by `preset.kind`
4. Parse and validate the stored `params`
5. Call the matching existing RM report method
6. Reuse existing JSON / CSV response semantics

### Kind mapping

- `ACTIVITY_FAMILY_REPORT`
  - `getActivityFamilyReport(from, to, eventTypeLimit, contributorLimit, format)`
- `ACTIVITY_EVENT_TYPE_REPORT`
  - `getActivityEventTypeReport(from, to, limit, format)`
- `ACTIVITY_CONTRIBUTOR_REPORT`
  - `getActivityContributorReport(from, to, limit, eventTypeLimit, format)`
- `ACTIVITY_CONTRIBUTOR_FAMILY_REPORT`
  - `getActivityContributorFamilyReport(from, to, limit, format)`
- `ACTIVITY_CONTRIBUTOR_EVENT_TYPE_REPORT`
  - `getActivityContributorEventTypeReport(from, to, limit, eventTypeLimit, format)`
- `ACTIVITY_FAMILY_HIGHLIGHTS`
  - JSON only, dispatch to `getActivityFamilyHighlights(windowDays)`
- `ACTIVITY_FAMILY_MIX`
  - JSON only, dispatch to `getActivityFamilies(days)`

### Param validation

Preset params stay stored as `Map<String, Object>`, but execute is explicit
about what it accepts:

- report kinds require `from` and `to` as ISO-8601 datetimes
- optional numeric knobs such as `limit`, `eventTypeLimit`,
  `contributorLimit`, `windowDays`, and `days` must be integers
- invalid or missing required params return `400`

### Explicit limits

- no arbitrary runtime param override on the execute route
- no cross-user execution
- no CSV support for summary-only preset kinds
- no scheduled delivery in this slice

## Outcome

`PR-83` preset storage is no longer only CRUD. It now has a minimal backend
execution path that turns a saved preset back into an existing RM report
response without introducing a second reporting surface.
