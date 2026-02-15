# Phase 54 - Admin Preview Diagnostics UI (Verification) - 2026-02-13

## Automated Checks

### 1) UI E2E (Mocked API, No Docker Required)

This test validates:
- page renders with E2E auth bypass
- summary chips match sample data
- filter works
- retry is enabled only for retryable failures

Command:

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:3000 npx playwright test e2e/admin-preview-diagnostics.mock.spec.ts --project=chromium --workers=1
```

Status: PASS (2026-02-13)

Notes:
- To avoid CRA dev-server rebuilds (and deep-link routing issues in simple static servers), you can serve the production build and run the test against it:

```bash
cd ecm-frontend
(cd build && python3 -m http.server 5500)
ECM_UI_URL=http://localhost:5500 npx playwright test e2e/admin-preview-diagnostics.mock.spec.ts --project=chromium --workers=1
```

### 2) UI + API E2E (Real Backend Required)

This test validates:
- create folder under `Documents`
- upload an `application/octet-stream` `.bin`
- backend marks preview as UNSUPPORTED
- diagnostics endpoint returns the item
- UI page shows it and filter finds it

Command:

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 npx playwright test e2e/admin-preview-diagnostics.spec.ts --project=chromium --workers=1
```

## Manual Smoke

1. Login as `admin`.
2. Open `/admin/preview-diagnostics`.
3. Confirm you see recent failures, and that **Retry/Force rebuild** are disabled for UNSUPPORTED/PERMANENT.
4. Click the search icon to open the item in Advanced Search.

## Results (2026-02-13)

- Mocked E2E: ✅ `admin-preview-diagnostics.mock.spec.ts`
- Real backend E2E: ⏳ pending (requires Docker stack running at `http://localhost:7700`)
