# Design: E2E Full Suite Run (2026-01-06)

## Goal
- Execute the full Playwright suite to validate end-to-end flows across UI, search, rules, and security.

## Approach
- Run `npx playwright test` against the configured UI/API endpoints.
- Record overall pass/fail counts and runtime summary.

## Files
- ecm-frontend/e2e/*
