# Verification: Audit Export Blank Parameter Guard (2026-01-06)

- `cd ecm-core && mvn -Dtest=AnalyticsControllerTest test`
- Result: pass (AnalyticsControllerTest: 9 tests). Blank parameters rejected.
