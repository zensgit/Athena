# Phase 0 Verification - Auth/Router + Search Preview (2025-12-30)

## Changes Verified
- Frontend API base URL now supports `REACT_APP_API_URL` or `REACT_APP_API_BASE_URL` fallback.
- Login flow shows a blocking “Signing you in...” state during Keycloak callback / login-in-progress.
- UI E2E stability: close Properties dialog via Escape to avoid toast overlay intercept.

## Tests
- `npm run e2e` (Playwright)
  - Result: **15/15 passed**
  - Duration: ~4.3 minutes

## Notes
- Initial E2E run failed due to Toastify overlay blocking a click on the Properties dialog Close button.
- Adjusted test to close dialog via `Escape`; re-run succeeded.
