# Design: Analytics Dashboard Aggregation (2026-01-06)

## Goal
- Verify the dashboard endpoint aggregates summary, storage, activity, and top user data.

## Approach
- Mock the analytics service responses for summary, storage, activity, and top users.
- Assert the dashboard JSON structure and values.

## Files
- ecm-core/src/test/java/com/ecm/core/controller/AnalyticsControllerTest.java
