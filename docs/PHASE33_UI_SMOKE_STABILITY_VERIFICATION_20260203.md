# Phase 33 UI Smoke Stability (Verification)

## Environment
- UI: `http://localhost:3000`
- API: `http://localhost:7700`

## Command
```
ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 ECM_E2E_SKIP_LOGIN=1 npx playwright test e2e/ui-smoke.spec.ts --reporter=line
```

## Result
- ✅ 10 passed (2.4m)

## Notes
- Verified PDF search/download card lookup works reliably after index refresh.
- No dev-server TypeScript overlay interruptions observed.
