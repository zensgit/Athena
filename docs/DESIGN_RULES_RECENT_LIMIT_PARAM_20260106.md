# Design: Rules Recent Limit Parameter (2026-01-06)

## Goal
- Ensure the recent rule activity endpoint respects an explicit limit parameter.

## Approach
- Mock a small result set and verify the controller calls `getRecentRuleActivity` with the requested limit.
- Assert a representative item is returned in the response.

## Files
- ecm-core/src/test/java/com/ecm/core/controller/AnalyticsControllerTest.java
