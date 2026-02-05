# PHASE47_MAIL_REPORTING_DEV_20260204

## Scope
- Add mail automation reporting aggregates (account + rule + daily trend) and CSV export.
- Surface reporting panel in Mail Automation UI with filters and summary tables.
- Extend mail automation E2E coverage to validate reporting panel rendering.

## Backend changes
- Added reporting aggregation queries in `ProcessedMailRepository` and DTO rows for account, rule, and trend aggregates.
- Added `MailReportingService` to resolve date ranges, aggregate totals, and build CSV exports.
- Added `/api/v1/integration/mail/report` and `/api/v1/integration/mail/report/export` endpoints with audit logging.
- Updated `MailAutomationControllerTest` constructor wiring.

## Frontend changes
- Added mail reporting response/types + API calls in `mailAutomationService`.
- Added Mail Reporting panel to `MailAutomationPage` with filters (account, rule, days), summary chips, account/rule tables, trend bars, and CSV export.
- Added lightweight formatting helpers for date/count display.

## Notes
- Reporting range defaults to the last 7 days, capped at 120 days in backend.
- Trend rows are filled for missing dates to keep UI consistent.
