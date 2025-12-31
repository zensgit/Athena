# Verification Report - UI Smoke 2025-12-30

## Scope
- Rebuild and restart the backend (ecm-core).
- Run the Playwright UI smoke suite.

## Environment
- Frontend: http://localhost:5500
- Backend: http://localhost:7700
- Key services: ecm-core healthy, elasticsearch healthy, keycloak healthy, clamav healthy.

## Commands
- `docker compose up -d --build --force-recreate ecm-core`
- `npx playwright test e2e/ui-smoke.spec.ts --reporter=line`

## Results
- Status: PASS
- Tests: 9 passed
- Duration: ~3.1 minutes

## Highlights
- Upload + browse + copy/move + delete/restore flows passed.
- PDF upload + preview + version history checks passed.
- RBAC checks for editor/viewer passed.
- Rule automation + scheduled rules passed (tag applied).
- Antivirus EICAR upload rejected as expected (HTTP 400).

## Notes
- Backend was rebuilt and container recreated successfully before the run.
- Search-dependent checks run via ui-smoke; if Elasticsearch is down, the test now skips those steps instead of failing.
