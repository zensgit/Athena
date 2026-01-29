# Design: Mail Rule Enable/Disable (2026-01-29)

## Goals
- Allow temporarily disabling mail rules without deleting them.
- Ensure fetcher skips disabled rules.
- Make status visible in Mail Automation UI.

## Backend Design
- Add `mail_rules.enabled` (boolean, default true).
- Mail fetcher uses only enabled rules.
- Rule CRUD supports `enabled` field.

## Frontend Design
- Mail Rules table shows status chip (Enabled/Disabled).
- Rule form includes Enabled toggle.
- Quick toggle in list to enable/disable.

## Files
- `ecm-core/src/main/java/com/ecm/core/integration/mail/model/MailRule.java`
- `ecm-core/src/main/java/com/ecm/core/integration/mail/repository/MailRuleRepository.java`
- `ecm-core/src/main/java/com/ecm/core/integration/mail/service/MailFetcherService.java`
- `ecm-core/src/main/java/com/ecm/core/integration/mail/controller/MailAutomationController.java`
- `ecm-core/src/main/resources/db/changelog/changes/021-add-mail-rule-enabled.xml`
- `ecm-frontend/src/pages/MailAutomationPage.tsx`
- `ecm-frontend/src/services/mailAutomationService.ts`
