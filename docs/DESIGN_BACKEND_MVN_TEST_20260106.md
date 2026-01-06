# Design: Backend mvn test Run (2026-01-06)

## Goal
- Run the full backend test suite to validate service and controller changes.

## Approach
- Execute `mvn test` in `ecm-core` and capture pass/fail summary.

## Files
- ecm-core/src/test/java/
