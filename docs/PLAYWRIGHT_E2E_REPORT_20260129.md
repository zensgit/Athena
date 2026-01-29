# Playwright E2E Report (2026-01-29)

## Full Run (Initial)
Command:
```
cd ecm-frontend
ECM_UI_URL=http://localhost:3000 \
ECM_API_URL=http://localhost:7700 \
KEYCLOAK_URL=http://localhost:8180 \
ECM_E2E_USERNAME=admin \
ECM_E2E_PASSWORD=admin \
npx playwright test
```
Result:
- 21 passed, 2 failed (mail automation actions + mail-automation diagnostics toast).
- Failure causes:
  - Mail diagnostics query failed (lower(bytea) JDBC error) from optional subject filter SQL.
  - UI smoke selector ambiguity (Refresh button/Processed section checks).

## Fixes Applied
- Use JPA Specification for processed mail filters (avoid null param SQL issues).
- Add Liquibase migration to normalize subject column when needed.
- Tighten Playwright selectors in `ui-smoke` mail automation test.

## Full Run (After Fixes)
Command:
```
cd ecm-frontend
ECM_UI_URL=http://localhost:3000 \
ECM_API_URL=http://localhost:7700 \
KEYCLOAK_URL=http://localhost:8180 \
ECM_E2E_USERNAME=admin \
ECM_E2E_PASSWORD=admin \
npx playwright test
```
Result:
- 23 passed, 0 failed (from Playwright report `stats.expected=23`, `stats.unexpected=0`).

## Targeted Re-runs
Commands:
```
cd ecm-frontend
ECM_UI_URL=http://localhost:3000 \
ECM_API_URL=http://localhost:7700 \
KEYCLOAK_URL=http://localhost:8180 \
ECM_E2E_USERNAME=admin \
ECM_E2E_PASSWORD=admin \
npx playwright test e2e/mail-automation.spec.ts

ECM_UI_URL=http://localhost:3000 \
ECM_API_URL=http://localhost:7700 \
KEYCLOAK_URL=http://localhost:8180 \
ECM_E2E_USERNAME=admin \
ECM_E2E_PASSWORD=admin \
npx playwright test e2e/ui-smoke.spec.ts -g "Mail automation actions"
```
Result:
- `mail-automation.spec.ts`: ✅ 2 passed
- `ui-smoke.spec.ts -g "Mail automation actions"`: ✅ 1 passed

## Notes
- Full suite re-run after fixes: ✅ passed.
