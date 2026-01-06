# Design: CI Test Pipeline (2026-01-06)

## Goal
- Ensure backend tests run in CI alongside existing frontend lint/test steps.

## Approach
- Update the existing GitHub Actions workflow to run `mvn test` for the backend job.
- Keep frontend lint/test coverage as-is.

## Files
- .github/workflows/ci.yml
