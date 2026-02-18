# Design: Settings Session Actions Mocked Coverage (2026-02-18)

## Background
- Settings page exposes session operations used in local diagnostics:
  - `Copy Access Token`
  - `Copy Authorization Header`
  - `Refresh Token`
- This path was not explicitly covered in mocked regression gate, increasing risk of silent UI regressions.

## Goal
- Add deterministic mocked E2E coverage for Settings session actions and include it in Phase 5 mocked gate.

## Scope
- Frontend E2E only.
- No backend API or auth protocol changes.

## Design Decisions
1. Add dedicated mocked spec:
   - File: `ecm-frontend/e2e/settings-session-actions.mock.spec.ts`
   - Session is seeded via bypass token.
   - Clipboard API is stubbed in-browser to verify copied content.
   - `/api/v1/**` dependencies used by Settings/MainLayout are mocked.

2. Assert both UX and payload:
   - Verify buttons are enabled.
   - Verify success toasts for copy/refresh actions.
   - Verify clipboard values:
     - raw token
     - `Authorization: Bearer <token>`

3. Promote to regression gate:
   - File: `scripts/phase5-regression.sh`
   - Add new mocked spec to `PHASE5_SPECS`.

## Expected Outcome
- Settings session operations become part of standard mocked regression.
- Faster detection of regressions around session utility actions and toast flows.
