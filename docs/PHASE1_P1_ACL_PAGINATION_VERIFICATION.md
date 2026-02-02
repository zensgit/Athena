# Phase 1 P1 - ACL Pagination Fix (Verification)

## Services
- API: `http://localhost:7700`
- UI: `http://localhost:3000`

## Search Index Rebuild
Command:
```bash
curl -X POST "http://localhost:7700/api/v1/search/index/rebuild" \
  -H "Authorization: Bearer <redacted>"
```
Result:
- `status: completed`
- `documentsIndexed: 253`

Status check:
```bash
curl "http://localhost:7700/api/v1/search/index/rebuild/status" \
  -H "Authorization: Bearer <redacted>"
```
Result:
- `inProgress: false`
- `documentsIndexed: 253`

## Frontend E2E (Playwright)
Command:
```bash
ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 \
  npx playwright test \
    ecm-frontend/e2e/search-sort-pagination.spec.ts \
    ecm-frontend/e2e/search-view.spec.ts \
    ecm-frontend/e2e/browse-acl.spec.ts
```
Result:
- `4 passed`

## Backend Tests
Command:
```bash
cd ecm-core && mvn test
```
Result:
- `BUILD SUCCESS`
- `Tests run: 129, Failures: 0, Errors: 0, Skipped: 0`
