# Phase 103: Startup Fallback False-Positive Guard

## Date
2026-02-24

## Background
- Phase102 introduced a static startup fallback overlay for blank-screen recovery.
- We also need explicit regression coverage to ensure normal startup paths do not trigger this fallback accidentally.

## Goals
1. Add a negative-path mocked E2E guard for normal startup.
2. Keep the guard in default mocked regression to avoid future false positives.

## Changes

### 1) Extended startup fallback mocked E2E
- File: `ecm-frontend/e2e/bootstrap-startup-fallback.mock.spec.ts`
- Added case:
  - `Startup fallback: normal startup does not show fallback overlay`
- Behavior:
  - sets fallback timeout override in E2E context
  - navigates to `/login`
  - verifies login shell is visible and fallback overlay count stays zero after timeout window

### 2) Gate effect
- File: `scripts/phase5-regression.sh`
- Existing inclusion of `bootstrap-startup-fallback.mock.spec.ts` now covers both forced-fallback and normal-startup-no-fallback assertions.
- Mocked total case count increases from `28` to `29`.

## Impact
- No backend/API changes.
- Adds guard against false-positive startup fallback overlays.
