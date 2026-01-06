# Design: Audit Recent Limit Parameter (2026-01-06)

## Goal
- Ensure the recent audit endpoint respects an explicit limit parameter.

## Approach
- Mock a small result set and verify the controller calls `getRecentActivity` with the requested limit.
- Assert a representative item is returned in the response.

## Files
- ecm-core/src/test/java/com/ecm/core/controller/AnalyticsControllerTest.java
