# UI E2E Verification (2025-12-28)

## Environment
- UI: http://localhost:5500
- API: http://localhost:7700
- Keycloak: http://localhost:8180

## Command
- `npm run e2e` (Playwright)

## Results
- Status: PASS
- Total: 15 tests passed
- Duration: ~3.3 minutes

## Coverage Highlights
- PDF preview (including fallback) and file browser view action.
- UI smoke: upload, search, version history, copy/move, facets, delete.
- RBAC: editor/viewer access restrictions.
- Rule automation + scheduled rules (manual trigger + tag verification).
- Security features: MFA guidance, audit export, retention.
- Antivirus: EICAR rejection verified.

## Report
- HTML report available via: `cd ecm-frontend && npx playwright show-report`
