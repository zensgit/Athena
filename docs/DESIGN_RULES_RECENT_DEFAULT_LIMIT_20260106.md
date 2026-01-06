# Design: Rules Recent Default Limit (2026-01-06)

## Goal
- Ensure the recent rule activity endpoint uses the default limit when no parameter is provided.

## Approach
- Mock the recent rule activity list and verify the controller calls `getRecentRuleActivity(20)`.
- Assert a representative item is returned in the response.

## Files
- ecm-core/src/test/java/com/ecm/core/controller/AnalyticsControllerTest.java
