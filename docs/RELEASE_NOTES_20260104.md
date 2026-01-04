# Release Notes - 2026-01-04

## Summary
- Implemented READ ACL filtering for search results and facets.
- Added audit export date range UI and fixed PDF preview layout.
- Hardened E2E flows and updated verification docs.

## Changes
- Backend: search ACL filtering in FullTextSearchService and FacetedSearchService.
- Frontend: Admin audit export From/To range inputs; PDF preview layout now flex-fills.
- E2E: improved login fallback, API readiness, preview/version validation.
- Docs: added step verification reports and updated audit/progress/regression summaries.

## Testing
- ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test (15 passed)
- docker run --rm -v "$(pwd)":/workspace -v "$HOME/.m2":/root/.m2 -w /workspace/ecm-core maven:3-eclipse-temurin-17 mvn test (17 tests, 0 failures)
- docker run --rm -v "$(pwd)":/workspace -v "$HOME/.m2":/root/.m2 -w /workspace/ecm-core maven:3-eclipse-temurin-17 mvn verify (17 tests, 0 failures)

## Docs
- docs/STEP_2_GAP_FIX_VERIFICATION.md
- docs/STEP_3_VERSION_DETAIL_VERIFICATION.md
- docs/STEP_4_PDF_PREVIEW_UI_VERIFICATION.md
- docs/STEP_5_BUILD_VERIFICATION.md
- docs/EXECUTION_AUDIT_REPORT.md
- docs/PROJECT_PROGRESS_SUMMARY.md
- docs/FINAL_REGRESSION_SUMMARY.md
- docs/SPRINT_2_SEARCH_AND_WEB_UI.md
- docs/SPRINT_3_SECURITY_COMPLIANCE_REPORT.md

## Notes
- Audit export uses browser-local datetime input; backend expects ISO timestamps.
- Lombok @Builder initialization warnings observed during build (non-blocking).
