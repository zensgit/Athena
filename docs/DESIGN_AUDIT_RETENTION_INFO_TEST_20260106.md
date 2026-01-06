# Design: Audit Retention Info Test (2026-01-06)

## Goal
- Ensure the audit retention endpoint returns expected policy details.

## Approach
- Mock retention days + expired log count.
- Assert JSON payload includes retention days, expired count, and export max range.

## Files
- ecm-core/src/test/java/com/ecm/core/controller/AnalyticsControllerTest.java
