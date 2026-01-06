# Design: Audit Export Blank Parameter Guard (2026-01-06)

## Goal
- Ensure blank `from`/`to` parameters return a 400 error.

## Approach
- Add controller tests that pass whitespace-only `from` and `to` values.
- Assert the endpoint rejects the request and does not call the service.

## Files
- ecm-core/src/test/java/com/ecm/core/controller/AnalyticsControllerTest.java
