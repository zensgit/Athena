# Phase 70: Auth/Route E2E Matrix - Verification

## Date
2026-02-19

## Scope
- Verify the new auth/route matrix scenarios pass reliably.
- Verify the dedicated Phase70 smoke wrapper runs with environment prechecks and succeeds end-to-end.

## Commands and Results

1. Phase70 smoke wrapper
```bash
bash scripts/phase70-auth-route-matrix-smoke.sh
```
- Result: PASS
- Playwright summary: `4 passed`

2. Direct Playwright run (targeted spec)
```bash
cd ecm-frontend
ECM_UI_URL=http://localhost \
ECM_API_URL=http://localhost:7700 \
KEYCLOAK_URL=http://localhost:8180 \
KEYCLOAK_REALM=ecm \
npx playwright test e2e/auth-route-recovery.matrix.spec.ts \
  --project=chromium --workers=1
```
- Result: PASS (`4 passed`)

## Validated Scenarios
1. Session-expired reason query shows login guidance and normalizes URL.
2. Redirect pause window shows operator guidance after protected-route handoff.
3. Unknown route recovers to login without blank page.
4. Login CTA reaches Keycloak authorize endpoint as terminal redirect state.

## Conclusion
- Auth/route recovery matrix is now covered by a deterministic dedicated E2E suite.
- Local one-command smoke execution is available for Day4 delivery and future regressions.
