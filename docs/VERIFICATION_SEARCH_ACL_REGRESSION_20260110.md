# Verification: Search ACL Regression Coverage (2026-01-10)

- `mvn test -Dtest=SearchAclFilteringTest`
- Result: pass (18 tests)

- `ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test e2e/search-view.spec.ts -g "Search results hide unauthorized documents for viewer" --project=chromium`
- Result: pass
