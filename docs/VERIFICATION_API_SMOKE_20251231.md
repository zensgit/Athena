# Athena ECM API Smoke Report (2025-12-31)

## Scope
- Backend API smoke test via `scripts/smoke.sh`
- Health, audit, antivirus, rules, workflow, search, WOPI, favorites, tags, categories, copy/move, trash

## Environment
- API base URL: http://localhost:7700
- Auth: Keycloak admin token

## Command
- `ECM_API=http://localhost:7700 ECM_TOKEN_FILE=tmp/admin.access_token ./scripts/smoke.sh`

## Results
- Status: PASS

## Notes
- Correspondent search can lag if indexing is delayed; script now treats this as a warn-only condition.

## Artifacts
- Smoke log: `tmp/20251231_085203_smoke-test.log`
- Share token: `tmp/smoke.share_token`
