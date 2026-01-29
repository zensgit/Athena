# Development: Processed Mail Retention (2026-01-29)

## Scope
- Add retention policy + cleanup for processed mail records.
- Expose retention status and manual cleanup actions in Mail Automation UI.
- Fix mail rule toggle request typing so UI compiles during automation tests.

## Backend Changes
- Added retention service with scheduled + manual cleanup.
- New endpoints:
  - `GET /api/v1/integration/mail/processed/retention`
  - `POST /api/v1/integration/mail/processed/cleanup`
- Repository helpers for retention cutoff deletes.
- New config default: `ecm.mail.processed.retention-days` (env `ECM_MAIL_PROCESSED_RETENTION_DAYS`, default 90).

## Frontend Changes
- Show retention chips (days + expired count) and add **Refresh Retention** / **Clean up expired** actions.
- Allow partial rule updates when toggling enabled state.

## Files Touched
- `ecm-core/src/main/java/com/ecm/core/integration/mail/service/MailProcessedRetentionService.java`
- `ecm-core/src/main/java/com/ecm/core/integration/mail/controller/MailAutomationController.java`
- `ecm-core/src/main/java/com/ecm/core/integration/mail/repository/ProcessedMailRepository.java`
- `ecm-core/src/main/resources/application.yml`
- `ecm-core/src/test/java/com/ecm/core/integration/mail/controller/MailAutomationControllerDiagnosticsTest.java`
- `ecm-core/src/test/java/com/ecm/core/integration/mail/controller/MailAutomationControllerSecurityTest.java`
- `ecm-core/src/test/java/com/ecm/core/integration/mail/controller/MailAutomationControllerTest.java`
- `ecm-core/src/test/java/com/ecm/core/integration/mail/service/MailFetcherServiceDiagnosticsTest.java`
- `ecm-frontend/src/pages/MailAutomationPage.tsx`
- `ecm-frontend/src/services/mailAutomationService.ts`

## Tests
- `cd ecm-core && mvn test`
- `cd ecm-frontend && npm run lint`
- `cd ecm-frontend && ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 KEYCLOAK_URL=http://localhost:8180 ECM_E2E_USERNAME=admin ECM_E2E_PASSWORD=admin npx playwright test e2e/mail-automation.spec.ts`
