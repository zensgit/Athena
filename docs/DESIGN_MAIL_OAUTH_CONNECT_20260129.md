# Design: Mail Automation OAuth Connect (2026-01-29)

## Goals
- Reduce OAuth2 setup friction for Gmail/Outlook IMAP accounts.
- Store refresh tokens securely server-side and reuse for IMAP fetch.
- Keep existing env-based credential key flow intact.

## Scope
- Backend endpoints to generate provider authorization URL and handle callbacks.
- Frontend Mail Automation UI to trigger connect and show status.
- Provider env variables for OAuth app credentials.

## Backend Design
- New endpoints (admin-only):
  - `GET /api/v1/integration/mail/oauth/authorize?accountId=...&redirectUrl=...`
  - `GET /api/v1/integration/mail/oauth/callback?code=...&state=...`
- OAuth state stored in-memory with 10 minute TTL (single-node compatible).
- Providers:
  - Google: `https://accounts.google.com/o/oauth2/v2/auth`
  - Microsoft: `https://login.microsoftonline.com/{tenant}/oauth2/v2.0/authorize`
- OAuth app env vars (server-side only):
  - `ECM_MAIL_OAUTH_GOOGLE_CLIENT_ID`
  - `ECM_MAIL_OAUTH_GOOGLE_CLIENT_SECRET`
  - `ECM_MAIL_OAUTH_MICROSOFT_CLIENT_ID`
  - `ECM_MAIL_OAUTH_MICROSOFT_CLIENT_SECRET`
  - Optional scopes:
    - `ECM_MAIL_OAUTH_GOOGLE_SCOPE`
    - `ECM_MAIL_OAUTH_MICROSOFT_SCOPE`
- Refresh tokens are stored in `mail_accounts.oauth_refresh_token`.
- Existing credential-key flow remains for CUSTOM/provider-agnostic use.

## Frontend Design
- Mail Automation accounts list:
  - Show OAuth connection status chip when `security=OAUTH2`.
  - Show "Connect OAuth" action for Google/Microsoft when not connected.
- Account form:
  - Credential key optional for Google/Microsoft.
  - Credential key required for CUSTOM providers.
  - Provider-specific env instructions displayed.
- OAuth callback redirects back to `/admin/mail` with `oauth_success` flag to toast.

## Trade-offs
- OAuth state is stored in-memory (not cluster-safe).
- Refresh token stored in DB (must be protected via DB security and backups).

## Files
- `ecm-core/src/main/java/com/ecm/core/integration/mail/service/MailOAuthService.java`
- `ecm-core/src/main/java/com/ecm/core/integration/mail/controller/MailAutomationController.java`
- `ecm-core/src/main/java/com/ecm/core/integration/mail/service/MailFetcherService.java`
- `ecm-frontend/src/pages/MailAutomationPage.tsx`
- `ecm-frontend/src/services/mailAutomationService.ts`
