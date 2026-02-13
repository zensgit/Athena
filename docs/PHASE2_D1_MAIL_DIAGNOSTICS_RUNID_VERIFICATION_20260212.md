# Phase 2 Day 1 (P0) - Mail Diagnostics Run ID (Verification)

Date: 2026-02-12

## Environment

- UI: `http://localhost:5500`
- API: `http://localhost:7700`
- Keycloak: `http://localhost:8180`

Docker services verified via `docker compose ps` (ecm-core + ecm-frontend healthy).

Note: this repo uses `docker-compose.override.yml` with `ecm-frontend/Dockerfile.prebuilt` which serves `ecm-frontend/build/`.
To validate UI changes in the container, `npm run build` must be run before rebuilding `ecm-frontend`.

## Backend Verification

### Unit tests

Run targeted unit test:

```bash
cd ecm-core
mvn -q test -Dtest=MailFetcherServiceDiagnosticsTest
```

Result: PASS

### API shape smoke (no secrets printed)

Acquire token:

```bash
./scripts/get-token.sh admin admin
```

Verify debug fetch returns `summary.runId`:

```bash
curl -sS -X POST \
  -H "Authorization: Bearer $(cat tmp/admin.access_token)" \
  "http://localhost:7700/api/v1/integration/mail/fetch/debug?force=true&maxMessagesPerFolder=1" \
  | jq -r '{summary_keys: (.summary|keys), runId: (.summary.runId // null)}'
```

Result: `runId` present and non-null.

## Frontend Verification

### Build + deploy to container

```bash
cd ecm-frontend
npm ci
npm run build
cd ..
docker compose up -d --build ecm-frontend
```

Result: container serves new JS bundle containing "Copy run id" UI strings.

### Playwright E2E

Run targeted test against docker UI/API:

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 \
  npx playwright test e2e/mail-automation.spec.ts -g "Mail automation test connection and fetch summary"
```

Result: PASS

Regression spot-check (search fallback governance still green):

```bash
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 \
  npx playwright test e2e/search-fallback-governance.spec.ts e2e/advanced-search-fallback-governance.spec.ts
```

Result: PASS (`5 passed`)

## Acceptance Check

- "Run Diagnostics" and "Trigger Fetch" produce `runId` values in API responses.
- Mail Automation UI surfaces run chips and supports copy-to-clipboard for correlation.

