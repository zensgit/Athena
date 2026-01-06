# Design: Audit Cleanup Response Tests (2026-01-06)

## Goal
- Verify the audit cleanup endpoint returns the correct deletion summary message.

## Approach
- Mock non-zero and zero deletion counts.
- Assert the JSON payload includes `deletedCount`, `retentionDays`, and the expected message.

## Files
- ecm-core/src/test/java/com/ecm/core/controller/AnalyticsControllerTest.java
