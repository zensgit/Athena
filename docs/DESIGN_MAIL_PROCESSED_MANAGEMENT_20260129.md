# Design: Processed Mail Management (2026-01-29)

## Goals
- Allow filtering processed mail diagnostics by status and subject.
- Support bulk deletion of processed mail records.
- Keep diagnostics export aligned with active filters.

## Backend Design
- Extend diagnostics filters:
  - `status`, `subject`, `processedFrom`, `processedTo` (optional).
- Add bulk delete endpoint:
  - `POST /api/v1/integration/mail/processed/bulk-delete` with `{ ids: [uuid] }`.
- Repository query supports optional filters (accountId, ruleId, status, subject, processedAt range).

## Frontend Design
- Diagnostics filter row adds:
  - Status selector (All/Processed/Error).
  - Subject contains filter.
- Processed messages table supports selection + bulk delete.
- Export CSV uses active filters.

## Files
- `ecm-core/src/main/java/com/ecm/core/integration/mail/repository/ProcessedMailRepository.java`
- `ecm-core/src/main/java/com/ecm/core/integration/mail/service/MailFetcherService.java`
- `ecm-core/src/main/java/com/ecm/core/integration/mail/controller/MailAutomationController.java`
- `ecm-frontend/src/pages/MailAutomationPage.tsx`
- `ecm-frontend/src/services/mailAutomationService.ts`
