# Design: Audit Export Response Headers (2026-01-06)

## Goal
- Confirm audit export responses include CSV content type, filename attachment header, and row count metadata.

## Approach
- Add a controller test that asserts the Content-Type, Content-Disposition filename, X-Audit-Export-Count, and CSV body.

## Files
- ecm-core/src/test/java/com/ecm/core/controller/AnalyticsControllerTest.java
