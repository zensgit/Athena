# Verification: Upload Dialog Auto-Close (2026-01-11)

## Scope
- Upload dialog auto-closes after successful upload in UI smoke flow.

## Environment
- UI: `http://localhost:5500`
- API: `http://localhost:7700`

## Command
```bash
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test e2e/ui-smoke.spec.ts:756
```

## Result
- ✅ Passed (1 test)
