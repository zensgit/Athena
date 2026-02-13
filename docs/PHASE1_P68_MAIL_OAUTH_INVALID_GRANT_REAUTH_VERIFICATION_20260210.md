# Phase 1 P68 Verification: Mail OAuth invalid_grant Reauth Loop

Date: 2026-02-10

## Scope

Verify that:

- Backend parses OAuth token endpoint errors and formats a safe, actionable message.
- Frontend Mail Automation page shows reauth-required state clearly (chips + reconnect action).
- Existing Mail Automation E2E remains stable.

## Automated Verification

### Backend (targeted unit test)

Command:

```bash
cd ecm-core
mvn -q -Dtest=MailOAuthTokenErrorParserTest test
```

Result:

- ✅ Pass

### Frontend (Playwright E2E)

Command:

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test e2e/mail-automation.spec.ts
```

Result:

- ✅ 7 passed
- ✅ 4 skipped (no runtime data / no replay candidates, etc.)

## Manual Sanity (optional)

If you can reproduce `invalid_grant` (revoked refresh token), expected behavior is:

1. Trigger `Test Connection` or `Trigger Fetch`.
2. Backend records `lastFetchError` beginning with `OAUTH_REAUTH_REQUIRED: invalid_grant ...`.
3. UI shows:
   - "OAuth reauth required" chip
   - Connect OAuth action as "Reconnect OAuth"

## Files Changed

- Backend:
  - `ecm-core/src/main/java/com/ecm/core/integration/mail/service/MailFetcherService.java`
  - `ecm-core/src/main/java/com/ecm/core/integration/mail/service/MailOAuthReauthRequiredException.java`
  - `ecm-core/src/main/java/com/ecm/core/integration/mail/service/MailOAuthTokenErrorParser.java`
  - `ecm-core/src/test/java/com/ecm/core/integration/mail/service/MailOAuthTokenErrorParserTest.java`
- Frontend:
  - `ecm-frontend/src/pages/MailAutomationPage.tsx`
- Docs:
  - `docs/PHASE1_P68_MAIL_OAUTH_INVALID_GRANT_REAUTH_DESIGN_20260210.md`
  - `docs/PHASE1_P68_MAIL_OAUTH_INVALID_GRANT_REAUTH_VERIFICATION_20260210.md`

