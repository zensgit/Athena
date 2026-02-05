# Athena ECM Delivery Checklist (2026-02-05)

## Deliverables
- [x] Code merged to `main`
- [x] Tag created: `v2026.02.05`
- [x] GitHub Release created/updated
- [x] Phase docs (47–53)
- [x] Rollup docs (design/dev/verification/changelog/release notes/consolidated report)
- [x] Acceptance checklist
- [x] Rollback impact assessment
- [x] Go‑live checklist
- [x] Pre‑release summary

## Verification
- [x] Backend tests: `mvn test`
- [x] Frontend E2E: `npx playwright test` (36 passed)
- [x] Health checks: API/UI/Keycloak/WOPI(Search/Mail)

## Handoff Notes
- WOPI health requires auth (401 without token; 200 with token).
- New permission template version table can remain on rollback.
