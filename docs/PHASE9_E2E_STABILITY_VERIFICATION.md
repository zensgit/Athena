# Phase 9 – Verification

## Automated Tests
- `cd ecm-frontend && npx playwright test`
  - Result: **27 passed** (full suite)

## Targeted Checks (during debugging)
- `cd ecm-frontend && npx playwright test e2e/ui-smoke.spec.ts -g "UI smoke: browse \+ upload"` ✅
- `cd ecm-frontend && npx playwright test e2e/ui-smoke.spec.ts -g "UI smoke: PDF upload"` ✅
- `cd ecm-frontend && npx playwright test e2e/mail-automation.spec.ts -g "lists folders"` ✅
