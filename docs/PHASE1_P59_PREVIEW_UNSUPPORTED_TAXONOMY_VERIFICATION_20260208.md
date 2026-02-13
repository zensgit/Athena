# Phase 1 P59 - Preview Unsupported Taxonomy (Verification) (2026-02-08)

## Scope

Verify that preview generation for unsupported content is categorized as `UNSUPPORTED` (not `FAILED`), and that search UI reflects this taxonomy:

- Search Results: "Preview unsupported" is neutral and has no retry actions.
- Advanced Search: `previewStatus=UNSUPPORTED` is supported and persists via URL.

## Prerequisites

- Docker stack running:
  - API: `http://localhost:7700`
  - UI: `http://localhost:5500`
  - Keycloak: `http://localhost:8180`

Note: the frontend container uses `ecm-frontend/Dockerfile.prebuilt` (copies `ecm-frontend/build/`), so source changes require `npm run build` before rebuilding the container.

## Verification Steps

### 1. Backend unit tests

Command:

```bash
cd ecm-core
mvn test
```

Expected:

- Build succeeds.
- Preview failure classifier tests include handling `previewStatus=UNSUPPORTED`.

Result:

- `BUILD SUCCESS` (150 tests).

### 2. Frontend lint + unit tests

Commands:

```bash
cd ecm-frontend
npm run lint
CI=true npm test -- --watchAll=false
```

Expected:

- ESLint passes.
- Jest suite passes (including `previewStatusUtils` tests).

Result:

- `eslint` passed.
- Jest: `10 passed` test suites.

### 3. Build + refresh runtime (`:5500`)

Commands:

```bash
cd ecm-frontend
npm run build

cd ..
docker compose up -d --build ecm-core ecm-frontend

curl -sS -o /dev/null -w "%{http_code}\n" http://localhost:7700/actuator/health
curl -sS -o /dev/null -w "%{http_code}\n" http://localhost:5500/
```

Expected:

- API and UI return `200`.
- On startup, Liquibase applies the new change set if not already applied.

Result:

- API health: `200`
- UI root: `200`

### 4. Playwright E2E (targeted)

Command:

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test e2e/search-preview-status.spec.ts
```

What it checks:

- Upload an `application/octet-stream` file.
- Call `GET /api/v1/documents/:id/preview` and assert:
  - `supported=false`
  - `failureCategory=UNSUPPORTED`
  - `status=UNSUPPORTED`
- Search Results UI shows "Preview unsupported" and hides retry actions.
- Advanced Search supports `previewStatus=UNSUPPORTED` in the URL and keeps the chip selected after reload.

Result:

- `3 passed`.

## Notes / Known Limitations

- Elasticsearch may temporarily contain older `previewStatus` values until reindexed.
  - The UI uses an effective status mapping (FAILED + unsupported reason/category/mime -> UNSUPPORTED) to remain accurate during this transition.

