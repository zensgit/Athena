# Phase 1 P52 - Auth Redirect Policy Config Design (2026-02-07)

## Background
- Redirect-failure policy now includes cooldown/cap/window logic.
- Hardcoded thresholds are inconvenient for different deployment environments.

## Goal
- Make redirect-failure thresholds configurable by frontend env vars while preserving safe defaults.
- Keep behavior deterministic when env values are invalid.

## Scope
- Frontend:
  - `ecm-frontend/src/constants/auth.ts`
  - `ecm-frontend/src/components/auth/Login.tsx`
  - tests

## Design Changes

### 1) Safe env parser
- Added `resolvePositiveIntEnv(raw, fallback)` utility:
  - undefined/non-numeric/non-positive => fallback
  - positive float => floor integer

### 2) Configurable redirect policy constants
- `AUTH_REDIRECT_FAILURE_COOLDOWN_MS`
  - env: `REACT_APP_AUTH_REDIRECT_FAILURE_COOLDOWN_MS`
  - default: `30000`
- `AUTH_REDIRECT_MAX_AUTO_ATTEMPTS`
  - env: `REACT_APP_AUTH_REDIRECT_MAX_AUTO_ATTEMPTS`
  - default: `2`
- `AUTH_REDIRECT_FAILURE_WINDOW_MS`
  - env: `REACT_APP_AUTH_REDIRECT_FAILURE_WINDOW_MS`
  - default: `300000`

### 3) UI message clarity
- Login pause message now includes current count/limit (e.g. `2/2`) for easier troubleshooting.

### 4) Test coverage
- Added `auth constants` unit tests for parser behavior and guardrails.
- Updated login test to assert count/limit hint appears.

## Compatibility
- No API/backend impact.
- Defaults maintain existing production behavior when env vars are absent.
