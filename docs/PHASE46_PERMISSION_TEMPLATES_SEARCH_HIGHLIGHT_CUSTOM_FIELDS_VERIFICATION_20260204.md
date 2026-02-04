# PHASE46_PERMISSION_TEMPLATES_SEARCH_HIGHLIGHT_CUSTOM_FIELDS_VERIFICATION_20260204

## Build & deployment
- `cd ecm-frontend && npm run build`
- `docker compose up -d --build ecm-core ecm-frontend`

## Automated verification (Playwright CLI)
- `ECM_E2E_SKIP_LOGIN=1 ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test e2e/permission-templates.spec.ts`
- `ECM_E2E_SKIP_LOGIN=1 ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test e2e/search-highlight.spec.ts`

## Results
- ✅ Permission template apply flow verified.
- ✅ Search highlight snippets verified.
