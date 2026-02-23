# Phase 86: Login Auth Handoff Status Card

## Date
2026-02-21

## Background
- Login page had status text messages but lacked a unified status-card structure for clear category separation.

## Goal
1. Consolidate auth-handoff warnings into one status card.
2. Keep timeout / generic init failure / session expired / redirect-failed states explicitly differentiated.

## Changes

### 1) Unified notice model
- File: `ecm-frontend/src/components/auth/Login.tsx`
- Added notice type:
  - `AuthInitNotice { title, detail }`
- Added resolver:
  - `buildAuthInitNotice(...)`
- Status mapping now yields a single structured card with distinct titles:
  - `Sign-in initialization timed out`
  - `Sign-in initialization failed`
  - `Session expired`
  - `Automatic sign-in needs retry`

### 2) UI card rendering
- File: `ecm-frontend/src/components/auth/Login.tsx`
- `Alert` now renders:
  - title (`Typography subtitle2`)
  - detail text
- Added test id:
  - `data-testid="login-auth-status-card"`

### 3) Test updates
- File: `ecm-frontend/src/components/auth/Login.test.tsx`
- Updated assertions to card-scoped checks (`within(...)`) to avoid duplicate text ambiguity.

## Impact
- No behavior break in login flow.
- Improves operator/user clarity during auth transition recovery.
