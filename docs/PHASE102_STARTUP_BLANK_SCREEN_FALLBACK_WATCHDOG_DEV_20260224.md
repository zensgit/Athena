# Phase 102: Startup Blank-Screen Fallback Watchdog

## Date
2026-02-24

## Background
- Some startup failure modes can occur before React error boundaries are mounted, leading to apparent blank/white startup screens.
- Existing protections focused on in-app runtime/auth recovery after React boot.

## Goals
1. Add pre-React startup watchdog fallback at static HTML layer.
2. Provide actionable recovery controls when startup stays blank.
3. Add mocked E2E and default gate coverage for this path.

## Changes

### 1) Static startup fallback in `index.html`
- File: `ecm-frontend/public/index.html`
- Added bootstrap watchdog script:
  - waits for app render mark (`window.__ECM_MARK_APP_RENDERED__`).
  - if startup remains blank past timeout, renders full-page fallback overlay with:
    - `Reload`
    - `Back to Login`
  - fallback message:
    - `Application startup is taking longer than expected. You can reload or return to login.`
- Includes E2E-only controls (webdriver/localhost):
  - `ecm_e2e_force_bootstrap_blank=1` (force fallback path)
  - `ecm_e2e_bootstrap_fallback_ms=<ms>` (override timeout)

### 2) Render mark integration
- File: `ecm-frontend/src/index.tsx`
- Added `markAppRendered()` bridge to call `window.__ECM_MARK_APP_RENDERED__`.
- Called after:
  - `renderAuthBooting()`
  - `renderApp()`
- Effect: fallback timer is canceled once app shell is rendered.

### 3) Mocked E2E + gate integration
- File: `ecm-frontend/e2e/bootstrap-startup-fallback.mock.spec.ts`
  - forces blank startup fallback via E2E keys.
  - verifies fallback overlay/action buttons.
  - verifies `Back to Login` recovery reaches login and shows app recovery status card.
- File: `scripts/phase5-regression.sh`
  - includes new spec in default mocked suite.
  - mocked case count increases from `27` to `28`.

## Impact
- No backend/API contract changes.
- Adds static-layer startup recoverability for pre-React blank-screen incidents.
