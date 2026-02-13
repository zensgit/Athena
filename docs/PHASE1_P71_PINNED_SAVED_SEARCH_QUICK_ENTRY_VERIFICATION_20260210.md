# Phase 1 (P71) - Verification: Pinned Saved Searches Quick Entry

Date: 2026-02-10

## What Was Verified
1. The `Saved` button is present on `SearchResults` next to `Advanced`.
2. The pinned saved searches menu loads data from the backend and lists pinned searches.
3. Clicking a pinned search executes it and updates the results list.
4. Folder scope mapping is correct (`folderId/includeChildren` reflected in SearchResults scope chip).

## Automated Verification (Playwright)
### Test Added
- `ecm-frontend/e2e/pinned-saved-search-quick-entry.spec.ts`

### Scenario
1. API ready check.
2. Obtain Keycloak access token for E2E user (default `admin/admin` unless overridden).
3. Upload a unique `.txt` document to root folder.
4. Wait for search index to include the uploaded filename.
5. Create a saved search via API that:
   - Queries by the uploaded filename.
   - Uses `filters.folderId=<rootId>` and `filters.includeChildren=false`.
6. Pin the saved search via API.
7. Navigate to `/search-results`.
8. Open `Saved` menu, click the pinned search.
9. Assert:
   - The uploaded filename appears in results.
   - The scope chip shows `Scope: This folder (no subfolders)`.

### Commands
Rebuild frontend prebuilt container (required for docker-based UI at `http://localhost:5500`):
```bash
./scripts/rebuild-frontend-prebuilt.sh
```

Run the P71 test:
```bash
cd ecm-frontend
npx playwright test e2e/pinned-saved-search-quick-entry.spec.ts
```

Optional full E2E regression:
```bash
cd ecm-frontend
npx playwright test
```

## Notes / Caveats
- The test creates a saved search and pins it. Cleanup is intentionally skipped to keep the test simple and avoid flaky teardown behavior; names are unique per run.
- No secrets are printed in test output or docs.

