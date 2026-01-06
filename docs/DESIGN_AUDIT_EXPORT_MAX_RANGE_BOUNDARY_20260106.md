# Design: Audit Export Max-Range Boundary (2026-01-06)

## Goal
- Ensure the audit export range limit is inclusive of the maximum window.

## Approach
- Add a controller test that requests a range equal to `auditExportMaxRangeDays`.
- Assert a successful response and expected `X-Audit-Export-Count` header.

## Files
- ecm-core/src/test/java/com/ecm/core/controller/AnalyticsControllerTest.java
