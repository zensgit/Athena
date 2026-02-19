# Phase 67: Settings Auth Recovery Debug Toggle - Development

## Date
2026-02-19

## Background
- Auth recovery debug events are already implemented (Phase 64), but local operators still need a browser-side switch without editing env/query parameters.
- Existing Settings page already provides session-level diagnostics actions and is the right place for this local toggle.

## Goal
1. Add a visible, user-controlled local switch for auth recovery debug logs.
2. Reuse shared utility methods instead of duplicating localStorage key handling.
3. Keep behavior safe by default and explicit when enabled by env/query override.

## Changes

### 1) Debug utility enhancement
- File: `ecm-frontend/src/utils/authRecoveryDebug.ts`
- Added exported helpers:
  - `AUTH_RECOVERY_DEBUG_STORAGE_KEY`
  - `isAuthRecoveryDebugLocalEnabled()`
  - `setAuthRecoveryDebugLocalEnabled(enabled)`
- `isAuthRecoveryDebugEnabled()` now reuses local helper for storage signal.

### 2) Settings diagnostics toggle
- File: `ecm-frontend/src/pages/SettingsPage.tsx`
- Added new `Diagnostics` card:
  - switch: `Enable auth recovery debug logs`
  - effective status display (`Enabled` / `Disabled`)
  - info hint when effective enablement comes from env/query instead of local switch
  - explanatory note for console prefix and redaction behavior
- Added toast feedback on toggle:
  - enabled: `Auth recovery debug logs enabled for this browser.`
  - disabled: `Auth recovery debug logs disabled for this browser.`
- `Copy Debug Info` payload now includes diagnostics flags:
  - `authRecoveryDebugLocalEnabled`
  - `authRecoveryDebugEffectiveEnabled`

### 3) Regression coverage update
- File: `ecm-frontend/e2e/settings-session-actions.mock.spec.ts`
- Extended existing mocked settings test to verify:
  - switch is visible and unchecked by default
  - enabling writes `localStorage.ecm_debug_recovery=1`
  - disabling clears the localStorage key
  - success toasts are shown for both transitions

### 4) Utility unit test update
- File: `ecm-frontend/src/utils/authRecoveryDebug.test.ts`
- Added helper-level test for local persist/clear behavior.

## Non-Functional Notes
- No backend changes or API contract changes.
- Default remains safe/off unless explicitly enabled.
