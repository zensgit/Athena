# Release Notes - 2026-01-11

## Summary
- Upload dialog now closes reliably after successful uploads.
- Full E2E suite rerun now passes after the fix.
- Verification and delivery documentation refreshed.

## Changes
- Frontend: Upload dialog uses a reset helper to close after success and clear files.
- E2E: Full Playwright suite rerun recorded as passing.
- Docs: Added design, verification, and delivery summary for the upload dialog fix.

## Testing
- ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test (19 passed)
- cd ecm-frontend && npm test -- --watchAll=false (4 suites, 11 tests)

## Notes
- Prior E2E failure at `e2e/ui-smoke.spec.ts:756:5` resolved by upload dialog auto-close fix.
