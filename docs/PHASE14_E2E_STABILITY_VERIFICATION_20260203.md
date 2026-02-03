# Phase 14 E2E Stability (Verification) - 2026-02-03

## Environment
- UI: `http://localhost:5500`
- API: `http://localhost:7700`
- Auth bypass: `ECM_E2E_SKIP_LOGIN=1` (frontend built with `REACT_APP_E2E_BYPASS_AUTH=1`).

## Playwright E2E (full)
Command:
```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 \
ECM_API_URL=http://localhost:7700 \
ECM_E2E_SKIP_LOGIN=1 \
npx playwright test
```

Result:
- **27 passed** (3.8m)

## Backend tests
Command:
```bash
cd ecm-core
mvn test
```

Result:
- **BUILD SUCCESS**
- Tests run: 135, Failures: 0, Errors: 0, Skipped: 0

## Targeted re-verification
Command:
```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 \
ECM_API_URL=http://localhost:7700 \
ECM_E2E_SKIP_LOGIN=1 \
npx playwright test e2e/mail-automation.spec.ts e2e/ui-smoke.spec.ts -g "Mail automation actions|Scheduled Rules"
```

Result:
- **2 passed**
