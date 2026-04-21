# P5 PR-93: RM Report Preset Scheduled Delivery Foundation Design

## Scope

This slice turns the shipped RM report preset foundation into a minimal
backend delivery workflow:

- schedule metadata on `rm_report_presets`
- manual delivery endpoint
- scheduled runner for due presets
- execution ledger for delivery results
- CSV upload into a configured target folder

It stays backend-only. No frontend schedule UI is introduced in this slice.

## Design

### Delivery shape

The design intentionally reuses the two thinnest existing seams already in the
repository:

- `PR-92` preset execute semantics for `preset -> report kind -> existing RM report`
- the mail scheduled export pattern for `scheduled run -> CSV -> upload -> last result`

There is no new generic scheduler framework.

### Supported preset kinds

Scheduled delivery is enabled only for CSV-capable report kinds:

- `ACTIVITY_FAMILY_REPORT`
- `ACTIVITY_EVENT_TYPE_REPORT`
- `ACTIVITY_CONTRIBUTOR_REPORT`
- `ACTIVITY_CONTRIBUTOR_FAMILY_REPORT`
- `ACTIVITY_CONTRIBUTOR_EVENT_TYPE_REPORT`

Summary-only kinds remain unsupported for scheduled delivery:

- `ACTIVITY_FAMILY_HIGHLIGHTS`
- `ACTIVITY_FAMILY_MIX`

This keeps delivery aligned with the existing export semantics and avoids
inventing JSON upload or second-surface behavior.

### Persistence changes

`rm_report_presets` now stores minimal schedule metadata:

- `schedule_enabled`
- `cron_expression`
- `schedule_timezone`
- `delivery_folder_id`
- `next_run_at`
- `last_run_at`

A new execution ledger table records each attempted delivery:

- preset id
- owner
- trigger type (`MANUAL` / `SCHEDULED`)
- status (`SUCCESS` / `FAILED`)
- filename
- target folder
- uploaded document id
- message
- start/end timestamps
- duration

### Service shape

`RmReportPresetDeliveryService` owns:

- schedule validation/update
- manual delivery
- periodic due-run polling
- execution ledger persistence

It reuses:

- `ScheduledRuleValidation` for cron/timezone validation and `nextRunAt`
- existing RM report methods for report generation
- existing `DocumentUploadService` for file delivery
- existing `AuditService` for RM delivery audit events

### Controller surface

The preset controller gains:

- `GET /api/v1/records/report-presets/{id}/schedule`
- `PUT /api/v1/records/report-presets/{id}/schedule`
- `POST /api/v1/records/report-presets/{id}/deliver`
- `GET /api/v1/records/report-presets/{id}/executions`

All remain owner-scoped through the preset service's existing ownership checks.

### Explicit limits

- no email channel yet
- no download-bundle channel yet
- no per-run parameter override
- no generic job framework
- no frontend schedule management in this slice

## Outcome

`PR-83` and `PR-92` now connect to a real recurring backend delivery path:
admins can persist a preset, execute it, configure a schedule, deliver it into
Athena content, and inspect the resulting execution ledger.
