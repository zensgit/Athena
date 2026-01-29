# Release Notes (2026-01-29)

## Highlights
- Processed mail retention policy with scheduled cleanup + manual cleanup controls.
- Mail Automation UI now surfaces retention status and expired counts.
- Mail rule enabled toggle now uses partial updates to avoid UI build errors.

## Features
- Processed mail retention service with configurable retention days.
- Admin endpoints for processed retention status + cleanup.
- Mail Automation UI retention chips + cleanup actions.

## Fixes
- Allow partial rule update payloads for enabled toggles.

## Configuration
- `ECM_MAIL_PROCESSED_RETENTION_DAYS` (default 90; set to 0 to disable).

## Verification
- `mvn test` ✅
- `npm run lint` ✅
- `npx playwright test e2e/mail-automation.spec.ts` ✅ (2 passed)

## Documentation
- `docs/DESIGN_MAIL_PROCESSED_RETENTION_20260129.md`
- `docs/DEVELOPMENT_MAIL_PROCESSED_RETENTION_20260129.md`
- `docs/VERIFICATION_MAIL_PROCESSED_RETENTION_20260129.md`
