# PR-41: RM Activity Audit Drilldown — Design

## Objective

Close the loop from RM analytics to evidence by letting operators jump from `RM Activity Highlights`, `RM Activity Breakdown`, and `RM Activity Timeline` into the existing `Records Audit` table with a prefilled date range.

This PR does not add a new analytics surface or a new audit model. It extends the existing `GET /api/v1/records/audit` filter contract with optional `to`, then reuses the current audit table on the RM admin page.

## Scope

- **In scope**
  - add optional `to` to `GET /api/v1/records/audit`
  - propagate `to` through controller -> service -> repository
  - keep exact closed-interval semantics for audit filtering
  - add frontend range drilldown entry points from:
    - `RM Activity Highlights`
    - `RM Activity Breakdown`
    - `RM Activity Timeline`
  - add an active drilldown banner above `Records Audit`
  - extend frontend audit filters with `To`
  - keep audit loading on the existing `loadAudit(...)` path
- **Out of scope**
  - new endpoints beyond extending `/records/audit`
  - new tables or new persistence models
  - per-series event-family drilldown
  - replacing the existing audit table

## Backend Design

### API Change

```http
GET /api/v1/records/audit?eventType=...&username=...&from=...&to=...&page=0&size=20
```

| Parameter   | Type          | Required | Description |
|-------------|---------------|----------|-------------|
| `eventType` | String        | No       | RM event type filter |
| `username`  | String        | No       | Acting user filter |
| `from`      | ISO DATE_TIME | No       | Inclusive lower bound |
| `to`        | ISO DATE_TIME | No       | Inclusive upper bound |
| `page`      | int           | No       | Page number |
| `size`      | int           | No       | Page size |

### Semantics

- `from` means `eventTime >= from`
- `to` means `eventTime <= to`
- `from` and `to` together form a closed `[from, to]` interval
- values stay exact `LocalDateTime`; backend does not perform date-only expansion
- admin-only access remains unchanged

### Backend Files

| File | Change |
|------|--------|
| `RecordsManagementController.java` | add optional `to` request param |
| `RecordsManagementService.java` | extend `listAudit(...)` with `to` |
| `AuditLogRepository.java` | add upper-bound clause to RM audit query |
| `RecordsManagementServiceTest.java` | cover `to` propagation and closed interval |
| `RecordsManagementControllerTest.java` | cover `to` request binding |

## Frontend Design

### Audit Drilldown Model

- keep the existing `Records Audit` table as the evidence surface
- add `to` to the existing frontend audit filter contract
- range drilldown always reuses the existing audit load path
- drilldown applies only a date range; it does not auto-apply `eventType` or `username`

### Drilldown Entry Points

- `RM Activity Highlights`
  - `Review current-window audit`
  - `Review previous-window audit`
- `RM Activity Breakdown`
  - one CTA per bucket: `Review audit for {bucket.label}`
- `RM Activity Timeline`
  - one CTA per day: `Review audit for {point.day}`

### Range Construction

- analytics surfaces emit day-granularity windows
- frontend expands those into audit filter values:
  - `from = YYYY-MM-DDT00:00:00`
  - `to = YYYY-MM-DDT23:59:59`
- the backend then applies them as an exact closed interval

### UX Rules

- drilldown scrolls to the existing `Records Audit` section
- an `info` banner above the audit table shows the active drilldown label and resolved range
- `Clear audit drilldown` clears only the active date-range drilldown
- existing `Apply` / `Clear` audit filter controls remain
- manual `Apply` or `Clear` exits drilldown mode and returns audit filtering to normal

### Frontend Files

| File | Change |
|------|--------|
| `recordsManagementService.ts` | add `to` to `RecordAuditFilters` and request params |
| `recordsManagementService.test.ts` | verify `to` is sent to `/records/audit` |
| `RecordsManagementPage.tsx` | add drilldown state, audit banner, `To` filter, CTA wiring |
| `RecordsManagementPage.test.tsx` | cover current-window, breakdown, timeline, and clear-drilldown flows |

## Compatibility

- existing callers that omit `to` keep prior behavior
- no response-shape change
- no migration required
- no existing analytics heading/copy was renamed
