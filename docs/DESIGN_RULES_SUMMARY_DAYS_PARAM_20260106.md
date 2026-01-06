# Design: Rule Execution Summary Days Parameter (2026-01-06)

## Goal
- Ensure the rule execution summary endpoint uses the default window and respects an explicit days parameter.

## Approach
- Mock rule execution summary data for default 7-day window and an explicit 14-day window.
- Assert summary fields and counts-by-type in the JSON response.

## Files
- ecm-core/src/test/java/com/ecm/core/controller/AnalyticsControllerTest.java
