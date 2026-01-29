# Design: Processed Mail Retention (2026-01-29)

## Goals
- Provide visibility into processed mail retention settings.
- Support manual cleanup of expired processed mail records.
- Run scheduled cleanup to keep the processed mail table bounded.

## Backend Design
- Add retention service with scheduled cleanup:
  - Uses `ecm.mail.processed.retention-days` (default 90).
  - Disabled when retention-days <= 0.
- New endpoints:
  - `GET /api/v1/integration/mail/processed/retention`
  - `POST /api/v1/integration/mail/processed/cleanup`
- Repository extensions:
  - `countByProcessedAtBefore(...)`
  - `deleteByProcessedAtBefore(...)`

## Frontend Design
- Show retention status alongside Processed Messages:
  - Retention days and expired count chips.
  - Refresh retention and manual cleanup buttons.
- Manual cleanup triggers API and refreshes diagnostics.

## Config
- `ECM_MAIL_PROCESSED_RETENTION_DAYS` (default 90)

## Files
- `ecm-core/src/main/java/com/ecm/core/integration/mail/service/MailProcessedRetentionService.java`
- `ecm-core/src/main/java/com/ecm/core/integration/mail/controller/MailAutomationController.java`
- `ecm-core/src/main/java/com/ecm/core/integration/mail/repository/ProcessedMailRepository.java`
- `ecm-core/src/main/resources/application.yml`
- `ecm-frontend/src/pages/MailAutomationPage.tsx`
- `ecm-frontend/src/services/mailAutomationService.ts`
