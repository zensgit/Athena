# Design: Audit Export Range Validation (2026-01-06)

## Goal
- Prevent empty or overly large audit export windows and expose export row counts for UI feedback.

## Approach
- Require `from` to be strictly before `to` to avoid empty ranges.
- Enforce a configurable max range via `ecm.audit.export.max-range-days` (default 90).
- Return `X-Audit-Export-Count` response header with row count for the export.

## Impact
- 400 responses include clear validation messages for invalid ranges.
- CSV payload format remains unchanged; a new response header is added.

## Files
- ecm-core/src/main/java/com/ecm/core/controller/AnalyticsController.java
- ecm-core/src/main/java/com/ecm/core/service/AnalyticsService.java
- ecm-core/src/main/java/com/ecm/core/config/SecurityConfig.java
