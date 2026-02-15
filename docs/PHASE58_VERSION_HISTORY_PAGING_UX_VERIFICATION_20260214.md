# Phase 58 - Version History: Paging UX + Major-Only Toggle (Verification)

Date: 2026-02-14

## What We Verify

- Version History dialog loads via the paged versions endpoint.
- `Load more` appends older versions (requests next page).
- `Major versions only` toggles the query to major-only results.

## Mocked E2E (No Backend Required)

### Build + Serve

```bash
cd ecm-frontend
npm run build
python3 -m http.server 5500 --directory build
```

### Run Test

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 npx playwright test \
  e2e/version-history-paging-major-only.mock.spec.ts \
  --project=chromium --workers=1
```

## Expected Results

- Playwright spec passes.
- The mocked `/documents/:id/versions/paged` handler observes:
  - `page=0` on initial open
  - `page=1` after `Load more`
  - `majorOnly=true` after enabling "Major versions only"

