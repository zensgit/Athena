# Phase 2 (Day 5) Design: Mail Report Scheduled Export

Date: 2026-02-12

## Goal

Provide a minimal, ops-friendly scheduled export for **Mail Reporting** that:

- runs on a cron schedule (disabled by default)
- exports the existing Mail Reporting CSV
- uploads the CSV into a configured ECM folder
- exposes a small status endpoint for the UI (read-only config + last export summary)
- supports an admin-triggered "run now" endpoint
- produces an audit trail

## Non-Goals

- No UI to edit cron/folder/filters (read-only UI is sufficient for Phase 2)
- No persistent export history (only last export summary is kept in-memory)
- No change to existing mail ingestion behavior or rules

## Architecture

### Backend Service

`MailReportScheduledExportService` is responsible for:

1. deciding whether an export should run (enabled + valid folder id)
2. generating the report via `MailReportingService`
3. exporting CSV via `MailReportingService.exportReportCsv(...)`
4. uploading the CSV via `DocumentUploadService.uploadDocument(...)`
5. recording an audit event and logging a concise summary
6. retaining the last export result for UI status

Implementation details:

- Scheduled entrypoint:
  - `@Scheduled(cron = "...")` calls `scheduledExport()`
- Manual entrypoint:
  - `exportNow(manual=true)` is used by the controller for admin-triggered execution
- Upload path:
  - uses `MockMultipartFile` to reuse the same upload pipeline as other integrations

### Configuration

All configuration is via Spring properties (default: **disabled**):

- `ecm.mail.reporting.export.enabled` (boolean, default `false`)
- `ecm.mail.reporting.export.cron` (cron, default `0 5 2 * * *`)
- `ecm.mail.reporting.export.folder-id` (UUID string, required when enabled)
- `ecm.mail.reporting.export.days` (int, default `30`)
- `ecm.mail.reporting.export.account-id` (UUID string, optional)
- `ecm.mail.reporting.export.rule-id` (UUID string, optional)

Env var mapping example (names only, values omitted):

- `ECM_MAIL_REPORT_EXPORT_ENABLED`
- `ECM_MAIL_REPORT_EXPORT_CRON`
- `ECM_MAIL_REPORT_EXPORT_FOLDER_ID`
- `ECM_MAIL_REPORT_EXPORT_DAYS`
- `ECM_MAIL_REPORT_EXPORT_ACCOUNT_ID`
- `ECM_MAIL_REPORT_EXPORT_RULE_ID`

### API Endpoints

Added to `MailAutomationController` (admin-only):

1. `GET /api/v1/integration/mail/report/schedule`
   - returns configured settings + `lastExport` summary (if any)
2. `POST /api/v1/integration/mail/report/schedule/run`
   - triggers export immediately and returns the `ScheduledExportResult`

### UI Surfacing

`MailAutomationPage` adds a read-only "Scheduled export" panel under the Mail Reporting card:

- enabled/disabled chip
- cron chip (tooltip shows full cron)
- days chip
- folder chip (best-effort path lookup by folderId)
- last export chip (status + tooltip message)

### Audit

On successful upload attempt, an audit event is recorded:

- event type: `MAIL_REPORT_SCHEDULED_EXPORTED`
- entity: uploaded document id (CSV)
- actor:
  - manual run: `"admin"` (because endpoint is admin-only)
  - scheduled run: `"scheduler"`
- metadata message includes schedule parameters (no secrets)

## Failure Modes / Behavior

- `enabled=false` => skip (no-op, success=true, status=SKIPPED)
- invalid/missing folder id => skip (no-op, success=true, status=SKIPPED)
- report generation/export/upload failure => attempted=true, success=false, status=ERROR, message populated

## Security Considerations

- Endpoints are `@PreAuthorize("hasRole('ADMIN')")`
- No secrets are stored in DB or returned by schedule status endpoints
- Report export writes a normal ECM document; folder permissions apply at the upload pipeline level

