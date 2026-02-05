# Athena ECM Pre‑Release Summary (2026-02-05)

## Health Checks
- API `/actuator/health` → 200
- UI `/` → 200
- Keycloak OIDC metadata → 200
- WOPI health: 401 without auth, 200 with auth
- Search basic query (auth) → 200
- Mail diagnostics (auth) → 200

## Tests
- Backend: `mvn test` (138 tests, BUILD SUCCESS)
- Frontend: `npx playwright test` (36 passed)
- CI: https://github.com/zensgit/Athena/actions/runs/21713803976

## Release Artifacts
- Tag: `v2026.02.05`
- Release notes: `docs/ROLLUP_RELEASE_NOTES_20260205.md`
- Acceptance: `docs/ROLLUP_ACCEPTANCE_CHECKLIST_20260205.md`
- Rollback: `docs/ROLLUP_ROLLBACK_IMPACT_20260205.md`
- Go‑live checklist: `docs/ROLLUP_GO_LIVE_CHECKLIST_20260205.md`
