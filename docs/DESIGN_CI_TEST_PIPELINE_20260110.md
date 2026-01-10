# Design: CI Test Pipeline (2026-01-10)

## Goal
- Expand backend CI coverage to include `mvn verify` packaging checks.

## Approach
- Update the backend job in GitHub Actions to run `mvn verify` instead of `mvn test`.
- Keep frontend lint, build, and unit test coverage unchanged.

## Files
- .github/workflows/ci.yml
