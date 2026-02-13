# Verification: Phase 1 P0 (Search totals/facets + Audit export fix + E2E stability) (2026-02-10)

## Scope
This verification covers:

- Search: accurate total hits + facet building via aggregations
- Audit export: fix PostgreSQL UUID NULL filter failure for optional `nodeId`
- E2E: ensure Playwright suite is stable after changes

## Environment
- UI: `ECM_UI_URL=http://localhost:5500`
- API: `ECM_API_URL=http://localhost:7700`

## Backend Build
From `ecm-core/`:

```bash
mvn -q -DskipTests package
```

Expected: build succeeds.

## Deploy / Restart API (Docker)
From repo root:

```bash
docker compose up -d --build ecm-core
docker inspect -f '{{.State.Health.Status}}' athena-ecm-core-1
```

Expected: `healthy`.

## Audit Export Regression Test (nodeId omitted)
Acquire an admin token:

```bash
bash scripts/get-token.sh admin admin
TOKEN="$(cat tmp/admin.access_token)"
```

Call export with `nodeId` omitted:

```bash
curl -sS -D /tmp/audit_export.headers -o /tmp/audit_export.csv \
  -H "Authorization: Bearer ${TOKEN}" \
  "http://localhost:7700/api/v1/analytics/audit/export?from=2026-02-09T00:00:00&to=2026-02-10T00:00:00"

head -n 2 /tmp/audit_export.headers
head -n 3 /tmp/audit_export.csv
```

Expected:

- HTTP 200
- CSV header row present
- No server-side 500

## Playwright (UI + API E2E)
From `ecm-frontend/`:

### Targeted smoke gate
```bash
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test e2e/ui-smoke.spec.ts
```

Expected: all tests pass.

### Full E2E suite
```bash
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test
```

Result (this change set):

- `49 passed`
- `3 skipped` (mail automation optional runtime panels)

## Notes
- `e2e/p1-smoke.spec.ts` was hardened to avoid `page.evaluate` failures caused by navigation races.
- `e2e/search-preview-status.spec.ts` now forces the binary sample to be classified `UNSUPPORTED` before validating the server-side UNSUPPORTED filter.

