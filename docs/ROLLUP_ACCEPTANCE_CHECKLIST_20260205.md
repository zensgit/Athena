# Athena ECM Acceptance Checklist (2026-02-05)

## Environment Readiness
- [x] API health responds (`/actuator/health` → 200)
- [x] UI reachable (`/` → 200)
- [x] Keycloak OIDC metadata reachable (`/.well-known/openid-configuration` → 200)
- [x] WOPI health (auth) responds (200)
- [x] Search endpoint returns 200 with auth
- [x] Mail diagnostics endpoint returns 200 with auth

## Feature Acceptance
### Mail Automation
- [x] Reporting panel renders with filters
- [x] Diagnostics dry‑run can list folders and show skip reasons
- [x] Connection summary shows OAuth status

### Search
- [x] Highlights appear in result snippets (where available)
- [x] ACL filtering enforced
- [x] Preview retry status visible
- [x] Bulk retry button present for failed previews

### Permission Templates
- [x] Version history lists snapshots
- [x] Compare view shows change summary and diff rows
- [x] CSV export downloads from compare dialog

### Versions / Preview
- [x] Version history compare summary displays
- [x] Preview retry action available in search

## Tests Executed
- [x] Backend: `cd ecm-core && mvn test` (BUILD SUCCESS, 138 tests)
- [x] Frontend: `npx playwright test` (36 passed)

## Notes
- WOPI health requires authentication (401 without auth; 200 with auth token).
