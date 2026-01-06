# Design: Analytics Storage-by-MIME Endpoint (2026-01-06)

## Goal
- Verify storage-by-MIME stats are returned by the endpoint.

## Approach
- Mock `getStorageByMimeType` and assert MIME type, count, and size fields.

## Files
- ecm-core/src/test/java/com/ecm/core/controller/AnalyticsControllerTest.java
