# Athena ECM Go‑Live Checklist (2026-02-05)

## Pre‑Go‑Live
- [ ] Confirm Docker services healthy (`docker ps` / health checks)
- [ ] API reachable (`/actuator/health` → 200)
- [ ] UI reachable (`/` → 200)
- [ ] Keycloak OIDC reachable (`/.well-known/openid-configuration` → 200)
- [ ] WOPI health (auth) → 200
- [ ] Mail diagnostics (auth) → 200
- [ ] Search basic query (auth) → 200

## Functional Smoke
- [ ] Mail Automation reporting panel renders
- [ ] Permission template history & compare loads
- [ ] CSV export downloads from compare dialog
- [ ] Search results show highlight snippets (where available)
- [ ] Preview retry status visible + bulk retry button present

## Test Evidence
- [x] Backend: `mvn test` (passed)
- [x] Frontend: Playwright full suite (36 passed)

## Rollback Readiness
- [ ] Tag `v2026.02.05` available
- [ ] Rollback doc reviewed (`docs/ROLLUP_ROLLBACK_IMPACT_20260205.md`)
