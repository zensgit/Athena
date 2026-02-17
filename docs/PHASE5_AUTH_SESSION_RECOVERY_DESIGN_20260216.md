# Phase5 Auth Session Recovery Design (2026-02-16)

## Background
- Search page under auth-bypass/e2e scenarios could redirect to `/login` after unrecoverable `401`, but user-facing "session expired" guidance was not consistently visible.
- Refresh-token failures were treated too aggressively in some cases, causing unnecessary logout on transient errors.

## Goals
- Keep user session on transient token-refresh failures.
- On unrecoverable `401`, provide deterministic login-page guidance ("Your session expired. Please sign in again.").
- Add stable regression coverage for the 401-retry-to-login flow.

## Scope
- Frontend auth service (`authService`) refresh error classification.
- Frontend API interceptor unrecoverable-401 redirect signaling.
- Login page fallback signal handling.
- Unit and Playwright E2E regression coverage.
- Phase5 regression script startup hardening for custom localhost ports.

## Design Changes
### 1) Refresh error classification
- File: `ecm-frontend/src/services/authService.ts`
- Added `shouldLogoutOnRefreshError(error)` with status/text heuristics:
  - transient/network/timeout/5xx/status-0 => do not logout
  - terminal auth errors (401/403/invalid_grant-like) => logout
- `refreshToken()` now preserves session for transient failures and returns current token when available.

### 2) Unrecoverable 401 redirect signaling
- File: `ecm-frontend/src/services/api.ts`
- Existing one-time retry behavior on 401 remains.
- When retry still fails (or request cannot be retried), mark session expired and redirect.
- Redirect now uses `/login?reason=session_expired` for deterministic login-page fallback.
- Storage markers remain best-effort (`sessionStorage` and `localStorage`) for backward compatibility.

### 3) Login fallback source expansion
- File: `ecm-frontend/src/components/auth/Login.tsx`
- Login warning now resolves session-expired reason from:
  - `sessionStorage` auth status
  - `localStorage` redirect reason
  - URL query `reason=session_expired`
- Added storage access guards for restricted contexts.
- Clears consumed query reason via `history.replaceState(...)` to avoid stale URL hints.

### 4) Regression gate startup hardening
- File: `scripts/phase5-regression.sh`
- When `ECM_UI_URL` points to localhost/127.0.0.1 and target is unreachable, script now auto-starts a local static server on the detected port.
- Startup previously only supported `:5500`; now custom ports (for example `:5514`) work without manual pre-start.
- Keeps current target guard (`check-e2e-target.sh`) and mocked-first execution strategy unchanged.

## Test Strategy
- Unit:
  - `authService` transient vs terminal refresh handling
  - `ApiService` 401 retry and unrecoverable marker behavior
  - Login warning resolution from storage and query fallback
- E2E:
  - mock flow where search request returns `401` twice (initial + one retry)
  - assert redirect to login and visible session-expired guidance

## Risk / Mitigation
- Risk: stale reason state on login screen.
  - Mitigation: clear storage markers and strip query reason on mount.
- Risk: flaky E2E due stale static server.
  - Mitigation: run E2E with dedicated port per run.
