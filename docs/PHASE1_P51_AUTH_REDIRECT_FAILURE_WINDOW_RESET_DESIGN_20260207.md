# Phase 1 P51 - Auth Redirect Failure Window Reset Design (2026-02-07)

## Background
- P50 introduced max auto redirect failure cap.
- Remaining issue: once cap is hit, auto redirect can stay paused too long unless user manually retries.

## Goal
- Make failure cap time-window based:
  - auto redirect recovers automatically after failure window expires.

## Scope
- Frontend:
  - `ecm-frontend/src/constants/auth.ts`
  - `ecm-frontend/src/components/auth/PrivateRoute.tsx`
  - `ecm-frontend/src/components/auth/Login.tsx`
  - auth tests

## Design Changes

### 1) Failure window constant
- Added `AUTH_REDIRECT_FAILURE_WINDOW_MS = 300000` (5 minutes).

### 2) PrivateRoute recovery logic
- Detects expired failure window using `AUTH_REDIRECT_LAST_FAILURE_AT_KEY`.
- When window expires:
  - clears failure count and timestamp,
  - treats failure count as reset (`0`),
  - allows auto redirect attempts again.
- Pause condition remains:
  - active short cooldown, or
  - failure count at cap within valid failure window.

### 3) Login message refinement
- When paused due to cap, message now includes approximate resume time when available.

### 4) Tests
- `PrivateRoute`:
  - added window-expiry reset test (auto redirect resumes after expiry).
- `Login`:
  - cap warning test now verifies resume-time hint.

## Compatibility
- No backend/API changes.
- Session-only state model preserved.
