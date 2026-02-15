# Phase 55 - Preview Diagnostics Hardening (Verification) - 2026-02-14

## Automated Checks

### 1) UI E2E (Mocked API, No Docker Required)

This spec now validates:

- page renders with E2E auth bypass
- summary chips match sample data
- filter works
- retry is enabled only for retryable failures
- **copy document id** calls clipboard (clipboard is stubbed to avoid OS permission flakiness)
- **open in Advanced Search** includes `previewStatus=FAILED` and `q=<name>`
- **open parent folder** resolves folder by path and navigates to `/browse/<folderId>`

Command (static build server, recommended for near-full disk environments):

```bash
cd ecm-frontend
npm run build
(python3 -m http.server 5500 --directory build)
ECM_UI_URL=http://localhost:5500 npx playwright test e2e/admin-preview-diagnostics.mock.spec.ts --project=chromium --workers=1
```

Status: PASS (2026-02-14)

### 2) UI + API E2E (Real Backend Required)

Integration spec (real backend):

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost ECM_API_URL=http://localhost:7700 npx playwright test e2e/admin-preview-diagnostics.spec.ts --project=chromium --workers=1
```

Status: PASS (2026-02-15)

Notes:
- The integration spec assertion was tightened to row-level matching (`tr` + `hasText`) so file name and path duplicate text no longer causes Playwright strict-mode false failures.
- Result: `1 passed (4.8s)`.

## Manual Smoke

1. Login as `admin`.
2. Open `/admin/preview-diagnostics`.
3. Confirm:
   - **Copy document id** copies a UUID to clipboard.
   - **Open parent folder** navigates to the containing folder.
   - **Open in Advanced Search** opens with a preview-status filter applied.
