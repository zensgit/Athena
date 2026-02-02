# Phase 13 - Search ACL Diagnostics Verification

## Environment
- Date: 2026-02-02
- API: http://localhost:7700
- UI: http://localhost:3000
- Auth bypass for E2E: `REACT_APP_E2E_BYPASS_AUTH=1`

## API Diagnostics Checks
```
# Admin
bash scripts/get-token.sh admin admin
curl -sS -H "Authorization: Bearer $(cat tmp/admin.access_token)" \
  http://localhost:7700/api/v1/search/diagnostics | jq -r '.'

# Viewer
bash scripts/get-token.sh viewer viewer
curl -sS -H "Authorization: Bearer $(cat tmp/viewer.access_token)" \
  http://localhost:7700/api/v1/search/diagnostics | jq -r '.'
```

### Observed
- Admin: `readFilterApplied=false` and `note=Admin role bypasses read filter.`
- Viewer: `readFilterApplied=true` and `note=Read filter applied to search results.`

## E2E (Search View)
```
cd ecm-frontend
ECM_UI_URL=http://localhost:3000 ECM_E2E_SKIP_LOGIN=1 npm run e2e -- search-view.spec.ts
```

### Result
- ❌ Failed (2 tests)
  - Error: `Search index did not include '<filename>'` after 60 attempts.
  - Likely search index delay/backlog after rebuilding `ecm-core`.

## Notes
- Search diagnostics endpoint is responsive and returns expected ACL scope for admin vs viewer.
- Retry the E2E once indexing catches up or after a manual index refresh.
