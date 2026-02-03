# Phase 15 - Version History Pagination + Major Filter (Verification) - 2026-02-03

## Manual Verification
1. Open a document with multiple versions.
2. Open **Version History**.
3. Confirm the list renders and shows "Showing X of Y versions".
4. Toggle **Major versions only** and confirm the list updates.
5. Click **Load more** until all versions are loaded.

## Automated Verification
- Run Playwright subset covering version flows.

```
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 ECM_E2E_SKIP_LOGIN=1 \
  npx playwright test e2e/version-details.spec.ts e2e/version-share-download.spec.ts
```

## Results
- 3 passed (6.9s).
