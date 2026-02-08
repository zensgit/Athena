# PHASE1 P60 - Folder-Scoped Search (Verification)

Date: 2026-02-09

## Environment

- Frontend (Docker): `http://localhost:5500`
- Backend (Docker): `http://localhost:7700`
- Keycloak: `http://localhost:8180`

## Build / Restart

Rebuild and restart core services:

```bash
bash scripts/restart-ecm.sh
```

## Automated Verification (Playwright)

Install frontend dependencies (for running local E2E):

```bash
cd ecm-frontend
npm ci --legacy-peer-deps --no-audit --no-fund
```

### 1) Folder-scoped search E2E

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 \
  npx playwright test e2e/folder-scoped-search.spec.ts
```

Expected:

- Search opened from `/browse/<folderId>` shows `Scope: This folder`
- Results contain only documents from that folder (until scope is cleared)

Result:

- ✅ `1 passed`

### 2) Regression subset: existing search flows

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 \
  npx playwright test \
    e2e/search-view.spec.ts \
    e2e/search-sort-pagination.spec.ts \
    e2e/folder-scoped-search.spec.ts
```

Result:

- ✅ `4 passed`

## Manual Smoke (Optional)

1. Open `http://localhost:5500/browse/<some-folder-id>`
2. Click the search icon
3. Confirm the dialog shows `Scope: This folder` and `Include subfolders`
4. Run a search and verify results are limited
5. Clear the scope chip on results page and verify results expand

