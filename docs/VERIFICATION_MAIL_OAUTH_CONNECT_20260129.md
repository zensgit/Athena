# Verification: Mail Automation OAuth Connect (2026-01-29)

## Prerequisites
- Configure OAuth app env vars on the API server:
  - `ECM_MAIL_OAUTH_GOOGLE_CLIENT_ID`, `ECM_MAIL_OAUTH_GOOGLE_CLIENT_SECRET`
  - `ECM_MAIL_OAUTH_MICROSOFT_CLIENT_ID`, `ECM_MAIL_OAUTH_MICROSOFT_CLIENT_SECRET`
- Ensure OAuth redirect URI matches:
  - `http://localhost:7700/api/v1/integration/mail/oauth/callback`

## Manual UI Flow
1. Go to `/admin/mail`.
2. Create or edit a mail account with:
   - Security: OAUTH2
   - Provider: GOOGLE or MICROSOFT
   - Username: mailbox email
3. Save the account.
4. Click **Connect OAuth** (login icon).
5. Complete provider consent.
6. Expected:
   - Redirect back to `/admin/mail?oauth_success=1&account_id=...`
   - Toast: “OAuth connected”.
   - Account row shows “OAuth connected”.
7. Click **Test connection**.
8. Expected: “Connection OK …”

## Frontend Lint
- Command: `cd ecm-frontend && npm run lint`
- Result: ✅ Passed

## Diagnostics Validation
- Trigger fetch (optional) and verify diagnostics:
  - `POST /api/v1/integration/mail/fetch`
  - `GET /api/v1/integration/mail/diagnostics`

## Result
- ⏳ Not run (requires live OAuth credentials and user consent).
