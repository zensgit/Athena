# Phase 22 E2E Auth Stability Verification (2026-02-03)

## Scenario
- Production build (no `REACT_APP_E2E_BYPASS_AUTH`)
- Headless Playwright run with `ECM_E2E_SKIP_LOGIN=1`

## Command
```bash
ECM_E2E_SKIP_LOGIN=1 ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 \
  npx playwright test e2e/mail-automation.spec.ts
```

## Result
`2 passed (25.2s)`

## Notes
- This run succeeds without rebuilding the UI with bypass flags.
- For comparison, running without `ECM_E2E_SKIP_LOGIN=1` still times out on `/login` in headless runs (Keycloak redirect does not complete in time).
