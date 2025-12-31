# Athena ECM Preview/UI Verification Report (2025-12-31)

## Scope
- Playwright subset: PDF preview + UI smoke
- Focus: preview rendering, search/upload flows, copy/move, rules, RBAC, antivirus

## Environment
- Frontend: http://localhost:5500
- Backend: http://localhost:7700
- Browser: Chromium (Playwright)

## Command
- `cd ecm-frontend && npx playwright test e2e/pdf-preview.spec.ts e2e/ui-smoke.spec.ts`

## Results
- Total: 12 passed, 0 failed
- Duration: ~3.1 minutes

## Artifacts
- HTML report snapshot: `tmp/20251231_130326_preview-e2e-report.html`
- Playwright report: `ecm-frontend/playwright-report/index.html`
