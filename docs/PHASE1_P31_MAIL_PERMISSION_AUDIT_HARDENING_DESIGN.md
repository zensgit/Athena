# Mail Automation P31 — Permission UX + Audit Hardening

Date: 2026-02-06

## Goal
Improve operator feedback when permissions are insufficient, and make diagnostics/runtime actions more auditable.

## Design
### Frontend
- Added explicit permission-denied handling for sensitive mail automation actions:
  - Runtime metrics fetch (`GET /integration/mail/runtime-metrics`)
  - Replay processed mail (`POST /integration/mail/processed/{id}/replay`)
- When backend returns `403`, UI now shows a clear toast:
  - "Permission denied: admin role required ..."

### Backend
- Diagnostics export audit payload was extended to include:
  - `requestId`
  - `sort`
  - `order`
  - export include-field flags
- Added dedicated runtime metrics view audit event:
  - `MAIL_RUNTIME_METRICS_VIEWED`

## Files Changed
- `ecm-core/src/main/java/com/ecm/core/integration/mail/controller/MailAutomationController.java`
- `ecm-frontend/src/pages/MailAutomationPage.tsx`

