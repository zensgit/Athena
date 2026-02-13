# Phase 1 (P73) - Verification: Saved Search CRUD Update

Date: 2026-02-10

## What Was Verified
1. Backend supports:
   - `GET /api/v1/search/saved/{id}`
   - `PATCH /api/v1/search/saved/{id}` (name and/or queryParams)
2. Frontend:
   - SearchResults deep-link execution loads saved search via get-by-id (not list+find).
   - Saved Searches page supports rename + duplicate via UI dialog.
3. End-to-end:
   - After updating `queryParams.query` via PATCH, deep-link execution returns the updated result set.

## Automated Verification (Playwright)
### Test Added
- `ecm-frontend/e2e/saved-search-crud-update.spec.ts`

### Scenario
1. API ready check.
2. Obtain Keycloak access token for E2E user.
3. Upload two unique documents (`docA`, `docB`).
4. Wait for search index to include both filenames.
5. Create a saved search (query = `docA`) with folder scope.
6. Navigate to `/saved-searches`:
   - Rename the saved search in UI.
   - Duplicate the saved search in UI.
7. Patch the original saved search:
   - Update `queryParams.query` to `docB` via `PATCH /api/v1/search/saved/{id}`.
8. Navigate to `/search-results?savedSearchId=<id>`:
   - Assert `docB` appears.
   - Assert scope chip shows `Scope: This folder (no subfolders)`.

### Commands
Rebuild backend and frontend containers:
```bash
docker compose up -d --build ecm-core
./scripts/rebuild-frontend-prebuilt.sh
```

Run the P73 test:
```bash
cd ecm-frontend
npx playwright test e2e/saved-search-crud-update.spec.ts
```

Optional full E2E regression:
```bash
cd ecm-frontend
npx playwright test
```

## Notes
- Frontend changes require rebuilding the prebuilt frontend container for `http://localhost:5500` to reflect source updates.
- No tokens are logged or stored in repo files during verification.

