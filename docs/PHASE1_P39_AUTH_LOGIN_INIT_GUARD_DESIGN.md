# Phase 1 P39: Auth Login Init Guard Design

## Date
2026-02-07

## Background

In production frontend (`http://localhost:5500/login`), clicking `Sign in with Keycloak` could fail with:

- `TypeError: Cannot read properties of undefined (reading 'login')`

Observed impact:

- User remains on login page or sees blank/unstable transition.
- Keycloak redirect does not happen.

## Root Cause

`AuthService.login()` called `keycloak.login()` without guaranteeing Keycloak runtime was initialized in all runtime modes.

Two contributing factors:

1. Bypass mode could be enabled too broadly (env-flag driven), including non-E2E manual browser sessions.
2. In those paths, `keycloak.init()` may not have run before `keycloak.login()`.

## Scope

- `ecm-frontend/src/services/authService.ts`

## Design Decisions

1. Restrict bypass to E2E browser context

- `getBypassMode()` now requires `window.navigator.webdriver === true` in browser.
- Env flag `REACT_APP_E2E_BYPASS_AUTH=1` is no longer enough by itself for normal user sessions.

2. Add login-time init guard

- Added `ensureKeycloakInitializedForLogin(keycloak)` to guarantee `keycloak.init(...)` runs before `keycloak.login(...)` if needed.
- Uses `onLoad: 'check-sso'`, `checkLoginIframe: false`, and conditional PKCE (`S256`) when Web Crypto is available.

3. Keep behavior compatible with existing E2E bypass flow

- Token-seeded bypass sessions still work for webdriver-based tests.
- Manual login path now remains safe and deterministic.

## Expected Outcome

- Clicking `Sign in with Keycloak` from login page always redirects to Keycloak auth page.
- No TypeError from auth service login flow.
- Existing preview/search Playwright E2E remains green.

