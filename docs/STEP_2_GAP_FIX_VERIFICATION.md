# Step 2 Verification: Search ACL + Audit Export Range

## Scope
- Enforce ACL filtering for search results and aggregations.
- Add audit export date range controls in Admin Dashboard.

## Changes Implemented
- `ecm-core/src/main/java/com/ecm/core/search/FullTextSearchService.java`: filter search results to READ-authorized nodes.
- `ecm-core/src/main/java/com/ecm/core/search/FacetedSearchService.java`: filter facet hits to READ-authorized nodes.
- `ecm-frontend/src/pages/AdminDashboard.tsx`: added From/To date-time inputs and export URL wiring.
- `ecm-frontend/e2e/ui-smoke.spec.ts`: fills From/To range before export.
- `docs/SPRINT_2_SEARCH_AND_WEB_UI.md`: updated security note to reflect ACL filtering.
- `docs/SPRINT_3_SECURITY_COMPLIANCE_REPORT.md`: removed TODO; documented range support.

## Verification
- UI test (audit export range):
  - Command: `ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test e2e/ui-smoke.spec.ts -g "Security Features"`
  - Result: ✅ Passed
- API test (offset normalization):
  - Rebuilt API: `docker compose up -d --build ecm-core`
  - Called `/api/v1/analytics/audit/export` with `from/to` in `-03:00` and `+08:00` representing the same instant (anchored to `/api/v1/analytics/audit/recent`); both returned matching row counts and included the target event.
- Backend tests (ACL via Elasticsearch):
  - Command: `cd ecm-core && mvn -Dtest=SearchAclElasticsearchTest test`
  - Result: ✅ Passed (uses `ECM_ELASTICSEARCH_URL` or defaults to `http://localhost:9200`)

## Notes
- ACL filtering uses `PermissionType.READ` for non-admins to align with existing authorization rules.
- Export range sends ISO-8601 with timezone offset; backend accepts offset or local time and normalizes to server time.
