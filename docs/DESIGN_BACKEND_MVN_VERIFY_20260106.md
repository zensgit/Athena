# Design: Backend mvn verify Run (2026-01-06)

## Goal
- Execute the full Maven verify lifecycle for backend validation.

## Approach
- Run `mvn verify` in `ecm-core` to cover tests plus packaging steps.

## Files
- ecm-core/
