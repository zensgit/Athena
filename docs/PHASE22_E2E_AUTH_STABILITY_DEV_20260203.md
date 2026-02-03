# Phase 22 E2E Auth Stability (2026-02-03)

## Goal
Improve login stability for headless Playwright runs by enabling a safe, automation-only auth bypass that does not require a special build flag.

## Changes
### Frontend
- Added a runtime “E2E bypass” mode when `navigator.webdriver === true` **and** localStorage flag `ecm_e2e_bypass=1` is present.
- This allows Playwright to inject a valid token/user into localStorage and skip Keycloak redirects in headless runs.
- Keeps production behavior unchanged for normal users (no bypass unless automation flag + explicit localStorage flag are set).

Files:
- `ecm-frontend/src/services/authService.ts`

### Playwright
- When `ECM_E2E_SKIP_LOGIN=1`, tests now set `localStorage.ecm_e2e_bypass=1` alongside the token/user.

Files:
- `ecm-frontend/e2e/*.spec.ts`

## Notes
- This bypass requires a valid access token and is only activated in automation contexts.
- Keycloak interactive login remains unchanged for real users.
