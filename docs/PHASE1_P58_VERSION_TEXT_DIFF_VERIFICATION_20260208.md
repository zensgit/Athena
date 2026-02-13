# Phase 1 P58: Version Compare Text Diff (Verification)

Date: 2026-02-08

## Environment
- Docker stack:
  - UI: `http://localhost:5500`
  - API: `http://localhost:7700`
  - Keycloak: `http://localhost:8180`

## Steps

### 1) Rebuild + Restart Services
Command:
```bash
docker compose up -d --build ecm-core ecm-frontend
```

Expected:
- `athena-ecm-core-1` becomes `healthy`
- `athena-ecm-frontend-1` starts and serves updated UI bundle

Result:
- PASS (containers rebuilt and recreated; `athena-ecm-core-1` reported healthy).

### 2) Backend Unit Test (Diff Utility)
Command:
```bash
cd ecm-core
mvn test -Dtest=LineDiffUtilsTest
```

Result:
- PASS
- `Tests run: 2, Failures: 0, Errors: 0`

### 3) Frontend Lint
Command:
```bash
cd ecm-frontend
npm run lint
```

Result:
- PASS (no eslint output).

### 4) E2E (Playwright)
Command:
```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 \
  npx playwright test e2e/version-share-download.spec.ts
```

Result:
- PASS
- `2 passed`
  - `Version history actions: download + restore`
  - `Share links enforce password, deactivation, and access limits`

## Evidence / What Was Verified
- Version compare dialog exposes a **Load text diff** action for text-like versions.
- Clicking **Load text diff** calls:
  - `GET /api/v1/documents/{documentId}/versions/compare?includeTextDiff=true&fromVersionId=...&toVersionId=...`
- UI renders a unified-like text diff containing:
  - `--- from` / `+++ to`
  - `- <initial marker>`
  - `+ <updated marker>`

