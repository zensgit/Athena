# Athena ECM API Smoke Report (2025-12-27)

## Scope
- Backend API smoke test via `scripts/smoke.sh`
- Health, audit, antivirus, rules, workflow, search, WOPI, favorites, tags, categories, copy/move, trash

## Environment
- API base URL: http://localhost:7700
- Auth: Keycloak admin token

## Command
- `./scripts/smoke.sh`

## Results
- Status: PASS (all checks completed)

## Key Checks
- Health/system status/license/sanity checks
- Audit export + retention
- Antivirus enabled + EICAR rejection
- RBAC: current user roles
- Admin users/groups CRUD
- Rules CRUD + trigger + audit summary
- Scheduled rules: manual trigger + auto-tag + audit log
- Favorites add/remove + batch checks
- Correspondent filter + facets
- WOPI: CheckFileInfo/GetFile/LOCK/PutFile/UNLOCK + version increment
- ML health
- Search indexing + saved searches
- Workflow approval start + completion
- Trash move + restore

## Artifacts
- Smoke log: `tmp/20251227_161246_smoke-test.log`
- Share token: `tmp/smoke.share_token`
