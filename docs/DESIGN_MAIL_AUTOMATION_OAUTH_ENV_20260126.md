# Design: Mail Automation OAuth Env Credentials + Fetch Observability (2026-01-26)

## Goals
- Remove OAuth secrets from the database/UI and load them only from server environment variables.
- Expose per-account last fetch timestamp/status/error for troubleshooting.
- Prevent Keycloak login failures when Web Crypto is unavailable in insecure contexts.

## Backend changes
- **MailAccount fields**: added `oauth_credential_key`, `last_fetch_at`, `last_fetch_status`, `last_fetch_error`.
- **OAuth credentials resolution (env-only)**:
  - `ECM_MAIL_OAUTH_<KEY>_CLIENT_ID` (required)
  - `ECM_MAIL_OAUTH_<KEY>_CLIENT_SECRET` (optional)
  - `ECM_MAIL_OAUTH_<KEY>_REFRESH_TOKEN` (required)
  - `ECM_MAIL_OAUTH_<KEY>_TOKEN_ENDPOINT` (optional override)
  - `ECM_MAIL_OAUTH_<KEY>_SCOPE` (optional override)
- **OAuth env self-check**:
  - Backend now evaluates required env keys per account.
  - `GET /api/v1/integration/mail/accounts` includes:
    - `oauthEnvConfigured: boolean`
    - `oauthMissingEnvKeys: string[]`
- **Token handling**: access tokens are cached in memory per account; no OAuth tokens/secrets are persisted.
- **Fetch observability**: each fetch attempt updates `last_fetch_*` on the account record with `SUCCESS` or `ERROR` plus a trimmed error message.
- **Migration**: OAuth secret columns are cleared to enforce env-only credentials.

## Frontend changes
- **Mail Automation form**:
  - New `OAuth credential key` input.
  - Server env helper text and masked placeholders for secrets.
  - OAuth secret inputs removed from the form.
- **Accounts table**: shows last fetch time/status; status chip tooltip displays last error.
- **OAuth env warning**: when `oauthEnvConfigured=false`, UI shows an `OAuth env missing` warning chip with missing keys tooltip.

## Auth resilience
- Keycloak PKCE is disabled automatically when Web Crypto is unavailable, avoiding `Web Crypto API is not available` errors in insecure contexts.

## API updates
- **MailAccount request**: secrets removed; `oauthCredentialKey` added.
- **MailAccount response**: includes `oauthCredentialKey`, `oauthEnvConfigured`, `oauthMissingEnvKeys`, `lastFetchAt`, `lastFetchStatus`, `lastFetchError`.

## Notes
- `oauthCredentialKey` is normalized to `ECM_MAIL_OAUTH_<KEY>_...` (uppercase, non-alphanumeric -> `_`).
- Ensure env vars are set before triggering OAuth mail fetch or test connection.
- `docker-compose.yml` now loads both `.env` and `.env.mail` into the `ecm-core` container via `env_file`.
- `.env.mail.example` provides a safe template; `.env.mail` is gitignored for secrets.
