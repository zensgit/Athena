# Design: Daily Activity Days Parameter (2026-01-06)

## Goal
- Ensure daily activity uses the default window and respects an explicit days parameter.

## Approach
- Mock daily activity stats for the default 30-day window.
- Mock daily activity stats for an explicit `days` parameter and verify the service call.

## Files
- ecm-core/src/test/java/com/ecm/core/controller/AnalyticsControllerTest.java
