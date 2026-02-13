# Phase 1 (P72) - Verification: Saved Search Share Link

Date: 2026-02-10

## What Was Verified
1. `SearchResults` executes a saved search when `savedSearchId` is present in the URL.
2. SearchResults UI reflects scoped filters (scope chip) after deep-link execution.

## Automated Verification (Playwright)
### Test Added
- `ecm-frontend/e2e/saved-search-share-link.spec.ts`

### Scenario
1. API ready check.
2. Obtain Keycloak access token for E2E user.
3. Upload a unique `.txt` document.
4. Wait for search index to include the filename.
5. Create a saved search with `filters.folderId` and `filters.includeChildren=false`.
6. Navigate to `/search-results?savedSearchId=<id>`.
7. Assert:
   - The uploaded filename appears in the results list.
   - Scope chip shows `Scope: This folder (no subfolders)`.

### Commands
Rebuild frontend prebuilt container (required for docker-based UI at `http://localhost:5500`):
```bash
./scripts/rebuild-frontend-prebuilt.sh
```

Run the P72 test:
```bash
cd ecm-frontend
npx playwright test e2e/saved-search-share-link.spec.ts
```

Optional full E2E regression:
```bash
cd ecm-frontend
npx playwright test
```

## Notes
- Saved searches are user-scoped. The deep link is intended for the same user account unless/until cross-user sharing is implemented.

