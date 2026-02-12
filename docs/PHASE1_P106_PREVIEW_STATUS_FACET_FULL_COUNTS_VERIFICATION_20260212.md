# Phase 1 P106 Verification: Preview Status Facet Counts From Full-Result Aggregations

Date: 2026-02-12

## Environment

- UI: `http://localhost:5500`
- API: `http://localhost:7700`
- Auth: Keycloak at `http://localhost:8180` (`admin/admin` via helper script)

## Verification Steps

### 1. Backend unit test (targeted)

```bash
cd ecm-core
mvn -q -Dtest=PreviewStatusFilterHelperTest test
```

Expected: exit code `0`.

### 2. Frontend build (Dockerfile.prebuilt copies local `build/`)

```bash
cd ecm-frontend
npm ci
npm run build
```

Expected: `Compiled successfully.`

### 3. Rebuild + restart containers

```bash
cd ..
docker compose up -d --build ecm-core ecm-frontend
curl -sS http://localhost:7700/actuator/health
```

Expected:
- API health: `{"status":"UP"}`

### 4. API sanity check: previewStatus facets return 6 stable buckets

Acquire an admin token:

```bash
bash scripts/get-token.sh admin admin
TOKEN="$(cat tmp/admin.access_token)"
```

Call faceted search with `facetFields=["previewStatus"]`:

```bash
curl -sS \
  -H "Authorization: Bearer ${TOKEN}" \
  -H 'Content-Type: application/json' \
  -X POST http://localhost:7700/api/v1/search/faceted \
  -d '{"query":"","filters":{},"pageable":{"page":0,"size":1},"facetFields":["previewStatus"]}' \
  | jq -c '.facets.previewStatus'
```

Expected:
- Always returns the buckets (0-count allowed) in order:
  - `READY`, `PROCESSING`, `QUEUED`, `FAILED`, `UNSUPPORTED`, `PENDING`

Observed (example on this environment):

```json
[{"value":"READY","count":550},{"value":"PROCESSING","count":16},{"value":"QUEUED","count":0},{"value":"FAILED","count":0},{"value":"UNSUPPORTED","count":73},{"value":"PENDING","count":22}]
```

### 5. Playwright E2E

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 \
  npx playwright test e2e/search-preview-status.spec.ts
```

Expected:
- All tests pass
- Includes: `Advanced search preview status facet counts reflect full result set`

Observed:
- `6 passed` (~13s)

