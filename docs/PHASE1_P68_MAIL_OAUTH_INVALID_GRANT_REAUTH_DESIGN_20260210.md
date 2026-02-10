# Phase 1 P68: Mail OAuth invalid_grant Reauth Loop

Date: 2026-02-10

## Problem

When an OAuth2 mail account refresh token is revoked/expired (for example Google returns `error=invalid_grant`),
Athena currently:

- Fails the mail fetch with a generic token refresh exception.
- Keeps the stored refresh token, so the UI can still show **"OAuth connected"** (token present).
- Provides no obvious "Reconnect OAuth" action, so the system can stay in a broken loop until someone manually fixes it.

## Goals

- Detect non-retryable OAuth token refresh failures (P0: `invalid_grant`).
- Force a clean "not connected" state by clearing stored OAuth tokens for the account.
- Surface a safe, actionable error string (`OAUTH_REAUTH_REQUIRED: ...`) without leaking secrets.
- Make the UI clearly indicate reauth is needed and allow the OAuth connect action to be used as remediation.

## Non-goals

- No new database columns or migrations in this phase.
- No change to the existing OAuth connect/callback flow.
- No automatic reauthorization (user must reconnect).

## Implementation

### Backend

- Add a small error parser:
  - `ecm-core/src/main/java/com/ecm/core/integration/mail/service/MailOAuthTokenErrorParser.java`
  - Best-effort parse of token endpoint error payloads (JSON preferred; heuristic fallback).
- Add a dedicated exception for UI-safe messaging:
  - `ecm-core/src/main/java/com/ecm/core/integration/mail/service/MailOAuthReauthRequiredException.java`
- Update token refresh to classify invalid_grant and reset tokens:
  - `ecm-core/src/main/java/com/ecm/core/integration/mail/service/MailFetcherService.java`
  - In `refreshOAuthAccessToken(...)`:
    - Catch `HttpStatusCodeException`
    - Parse `{error,error_description}`
    - If `error == invalid_grant`:
      - Clear stored OAuth tokens on the `MailAccount` (`oauthRefreshToken`, `oauthAccessToken`, `oauthTokenExpiresAt`)
      - Evict in-memory cached access token (`oauthSessionByAccount`)
      - Persist the cleared fields
      - Throw `MailOAuthReauthRequiredException` with message `OAUTH_REAUTH_REQUIRED: invalid_grant - ...`
  - In `fetchAllAccounts(...)`:
    - Catch `MailOAuthReauthRequiredException` separately to log a warning and store a clean, actionable `lastFetchError`.

### Frontend

- Improve the mail automation page to recognize reauth-required errors:
  - `ecm-frontend/src/pages/MailAutomationPage.tsx`
  - Add `isOauthReauthError(...)` helper.
  - Map the raw backend error string to a short actionable summary:
    - `OAuth reauth required (token revoked/expired). Reconnect OAuth.`
  - Show an "OAuth reauth required" warning chip on:
    - Connection Summary panel
    - Accounts table rows
  - Allow the OAuth connect action to appear as "Reconnect OAuth" when a reauth error is detected, even if `oauthConnected === true`.

## Acceptance Criteria

- If token refresh returns `invalid_grant`, Athena clears stored OAuth tokens and persists the change.
- UI indicates reauth is needed (warning chip + actionable message).
- No secrets appear in logs/UI messages.
- Automated checks pass:
  - Backend unit test for token error parsing.
  - Frontend Playwright `mail-automation.spec.ts` continues to pass.

