# Design: Backend mvn test Run (2026-01-10)

## Goal
- Re-run the backend test suite after mock maker configuration changes.

## Approach
- Execute `mvn test` in `ecm-core` and capture results.

## Files
- ecm-core/src/test/java/
