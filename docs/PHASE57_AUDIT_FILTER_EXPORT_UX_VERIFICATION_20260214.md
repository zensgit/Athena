# Phase 57 - Audit: Filtered Explorer + Export Presets UX (Verification)

Date: 2026-02-14

## What We Verify

- Audit filters persist in the URL (shareable).
- Filtered audit search sends normalized `eventType` codes (e.g., `NODE_CREATED`).
- Export triggers a download with a stable filename that includes:
  - date range label
  - `user-...`
  - `event-...`
  - `cat-...`
  - `node-<uuid8>`

## Mocked E2E (No Backend Required)

This uses a static build server and Playwright route mocking.

### Build + Serve

```bash
cd ecm-frontend
npm run build
python3 -m http.server 5500 --directory build
```

### Run Targeted Test

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 npx playwright test \
  e2e/admin-audit-filter-export.mock.spec.ts \
  --project=chromium --workers=1
```

### Run Mocked Regression Subset

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 npx playwright test \
  e2e/admin-preview-diagnostics.mock.spec.ts \
  e2e/permissions-dialog-presets.mock.spec.ts \
  e2e/admin-audit-filter-export.mock.spec.ts \
  --project=chromium --workers=1
```

## Expected Results

- All Playwright specs pass.
- The audit search request includes `eventType=NODE_CREATED` (not a human label like "Node Created").
- Download filename contains:
  - `audit_logs_YYYYMMDD_to_YYYYMMDD`
  - `user-<username>`
  - `event-<EVENT_CODE>`
  - `cat-<CATEGORY>`
  - `node-<uuid8>`

