# Phase 1 P69 - Mail OAuth Reset + OAuth Skip Strategy (Design)

Date: 2026-02-10

## Problem

Mail Automation OAuth accounts can get stuck in a noisy failure loop when:

- OAuth env configuration is missing (client id/secret/refresh token env vars).
- The account has not been connected yet (no refresh token persisted for non-credential-key accounts).
- A refresh token is revoked/expired (for example `invalid_grant`) and the UI keeps showing a sticky "reauth required" state.

We need an admin-safe way to clear OAuth state and make scheduled fetching skip unready OAuth accounts rather than failing repeatedly.

## Goals

- Add a single admin API to reset OAuth state for a mail account.
- Skip OAuth2 accounts during scheduled fetch when they are unconfigured or not connected, avoiding noisy errors.
- Provide a UI action to trigger the reset (with confirmation).
- Verify via Playwright CLI E2E without requiring real OAuth login flows.

## Non-Goals

- Implement full OAuth connection automation in E2E (requires external provider login).
- Change database schema or encrypt stored secrets (out of scope for this phase).

## Backend Changes

### API

- `POST /api/v1/integration/mail/accounts/{id}/oauth/reset`
  - Auth: `ROLE_ADMIN`
  - Behavior:
    - Clears stored OAuth token fields on the `MailAccount`:
      - `oauthAccessToken`
      - `oauthRefreshToken`
      - `oauthTokenExpiresAt`
    - Evicts in-memory OAuth access token cache for the account.
    - If the last fetch error indicates reauth is required (`OAUTH_REAUTH_REQUIRED:*`), clears:
      - `lastFetchError`
      - `lastFetchStatus`
  - Response: updated account DTO (same shape as `/accounts` list)

### Scheduled Fetch Skip Strategy

In `MailFetcherService.fetchAllAccounts(...)`, before processing an account:

- If OAuth env is unconfigured (`checkOAuthEnv(...).configured() == false`), skip the account:
  - Skip reason: `oauth_unconfigured`
  - Throttling: updates `lastPollByAccount` to avoid re-checking every minute.
- If the account is OAuth2 without credential key and has no stored refresh token, skip:
  - Skip reason: `oauth_not_connected`
  - No throttling (so once user connects, the next scheduled run can pick it up quickly).

The same skip decision is applied for debug runs (`fetchAllAccountsDebug`) as a non-attempted account result.

## Frontend Changes

- Add `mailAutomationService.resetOAuth(accountId)` calling the new backend endpoint.
- Add a "Reset OAuth" action (IconButton) on each OAuth2 account row:
  - Confirmation via `window.confirm(...)`
  - Shows spinner while executing
  - On success: toast + refresh data

## Edge Cases / Failure Modes

- If reset is called on a non-OAuth2 account: backend returns a 4xx error (invalid request).
- If env vars are missing: reset still succeeds (it only clears state).
- For credential-key accounts, refresh token is typically stored in env; reset does not and cannot alter env values.

## Security Notes

- No OAuth secrets are logged or returned by the reset endpoint.
- Error messages used for UI remain safe and should not contain tokens.

## Verification Plan

- Backend compile + unit test run.
- Frontend Playwright E2E:
  - Create a temporary disabled OAuth2 account via API (safe, no real OAuth).
  - Use UI to click "Reset OAuth" and accept confirmation.
  - Assert toast is shown.
  - Cleanup the account via API delete.

