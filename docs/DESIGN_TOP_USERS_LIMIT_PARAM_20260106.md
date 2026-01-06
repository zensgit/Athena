# Design: Top Users Limit Parameter (2026-01-06)

## Goal
- Ensure the top users endpoint uses the default limit and respects an explicit limit parameter.

## Approach
- Mock top user activity stats for default limit 10 and explicit limit 5.
- Assert the response payload and verify the service calls.

## Files
- ecm-core/src/test/java/com/ecm/core/controller/AnalyticsControllerTest.java
