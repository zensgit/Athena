# Mail Automation P2: OAuth2 IMAP Support

## Scope
- Add OAuth2-based IMAP authentication using XOAUTH2.
- Persist OAuth credentials/tokens on mail accounts.
- Enable token refresh via provider token endpoints.
- Expose OAuth configuration in the admin UI.

## Data Model
New `mail_accounts` fields:
- `oauth_provider` (`GOOGLE`, `MICROSOFT`, `CUSTOM`)
- `oauth_token_endpoint` (optional override)
- `oauth_client_id`, `oauth_client_secret`
- `oauth_tenant_id` (Microsoft)
- `oauth_scope`
- `oauth_access_token`, `oauth_refresh_token`
- `oauth_token_expires_at`

Migration: `ecm-core/src/main/resources/db/changelog/changes/015-add-mail-account-oauth-fields.xml`.

## Auth Flow
1. If `security = OAUTH2`, the fetcher uses XOAUTH2 SASL over IMAP.
2. Access token is used directly when valid.
3. If expired and refresh token is available, a refresh request is issued:
   - Google: `https://oauth2.googleapis.com/token`
   - Microsoft: `https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token`
   - Custom: `oauth_token_endpoint`
4. New access/refresh tokens are persisted to `mail_accounts`.

## UI
OAuth fields are shown only when `Security = OAUTH2`.
Secrets (client secret, tokens) are write-only in UI.

## Notes / Limitations
- Tokens are stored as plain text; consider encryption at rest for production.
- `OAUTH2` assumes SSL for IMAP; STARTTLS is not currently configurable.
- If token refresh is not configured, access tokens must be updated manually.
