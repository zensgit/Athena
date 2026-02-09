# Phase 1 P67 - Server-Side Preview Status Filtering (Verification) - 2026-02-09

## Scope

Verify that Preview Status filtering is applied **server-side** for:

- Search Results (`/search-results`)
- Advanced Search (`/search`)

And that pagination/totals reflect the filtered result set.

## Environment

- API: `http://localhost:7700`
- UI: `http://localhost:5500`
- Keycloak: `http://localhost:8180`

## Commands

### 1) Backend tests (Elasticsearch required)

```bash
cd ecm-core
mvn test -Dtest=SearchAclElasticsearchTest
```

Expected: `BUILD SUCCESS`

Result: ✅ `BUILD SUCCESS` (SearchAclElasticsearchTest)

Notes:
- Test uses `ECM_ELASTICSEARCH_URL` or defaults to `http://localhost:9200`.

### 2) Frontend build (Docker prebuilt UI uses `build/`)

```bash
cd ecm-frontend
npm run build
```

Expected: build succeeds.

Result: ✅ Built successfully (also verified during Docker image rebuild in step 3).

### 3) Restart stack (rebuild images)

```bash
cd ..
bash scripts/restart-ecm.sh
```

Expected: containers become healthy; UI/API reachable.

Result: ✅ OK (`bash scripts/restart-ecm.sh`)

Quick checks:

```bash
curl -sS http://localhost:7700/actuator/health
curl -sS -o /dev/null -w "%{http_code}\n" http://localhost:5500/
```

Observed:
- `/actuator/health`: `{"status":"UP"}`
- UI: `200`

### 4) Playwright E2E (targeted)

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 \
  npx playwright test e2e/search-preview-status.spec.ts
```

Expected:
- All tests pass.
- New test confirms server-side filtering by waiting for `GET /api/v1/search?...previewStatus=UNSUPPORTED`.

Result: ✅ `4 passed` (`e2e/search-preview-status.spec.ts`)

## Functional Checks Confirmed

- Search Results Preview Status chips apply across pages (server-side) and totals render `Showing X of Y results`.
- Advanced Search sends `filters.previewStatuses` to `/search/faceted` and keeps URL state stable.
