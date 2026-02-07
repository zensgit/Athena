# Mail Automation P29 — Runtime Observability Panel

Date: 2026-02-06

## Goal
Expose runtime health indicators for mail automation operations directly in admin UI.

## Design
### Backend
- Added endpoint:
  - `GET /api/v1/integration/mail/runtime-metrics?windowMinutes=`
- Added service aggregation (`MailFetcherService.getRuntimeMetrics`) returning:
  - `windowMinutes`
  - `attempts`
  - `successes`
  - `errors`
  - `errorRate`
  - `avgDurationMs`
  - `lastSuccessAt`
  - `lastErrorAt`
  - `status` (`HEALTHY|DEGRADED|DOWN|UNKNOWN`)
- Added runtime metrics view audit event:
  - `MAIL_RUNTIME_METRICS_VIEWED`

### Frontend
- Added `Runtime Health` card in Mail Automation page.
- Supports selectable time window (`60m/3h/12h/24h`).
- Added manual refresh button for runtime metrics.
- Added explicit `403` toast for non-admin access.

## Files Changed
- `ecm-core/src/main/java/com/ecm/core/integration/mail/controller/MailAutomationController.java`
- `ecm-core/src/main/java/com/ecm/core/integration/mail/service/MailFetcherService.java`
- `ecm-frontend/src/services/mailAutomationService.ts`
- `ecm-frontend/src/pages/MailAutomationPage.tsx`
