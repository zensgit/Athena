# Design: Backend mvn verify Run (2026-01-10)

## Goal
- Validate backend build + test suite via `mvn verify`.

## Approach
- Execute `mvn verify` in `ecm-core` and capture results, including any environment-specific failures.

## Files
- ecm-core/src/test/java/
- ecm-core/target/surefire-reports/
