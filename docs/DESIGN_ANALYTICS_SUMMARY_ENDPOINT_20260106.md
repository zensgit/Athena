# Design: Analytics Summary Endpoint (2026-01-06)

## Goal
- Verify the summary endpoint returns document/folder counts and total size.

## Approach
- Mock `getSystemSummary` and assert the JSON payload fields.

## Files
- ecm-core/src/test/java/com/ecm/core/controller/AnalyticsControllerTest.java
