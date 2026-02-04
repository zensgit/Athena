# Phase 39 - Search Preview Close Selector Fix (2026-02-04)

## Context
The full Playwright E2E run failed in `search-view.spec.ts` because `getByLabel('close')`
matched both the Toast close button and the preview close button, triggering Playwright
strict-mode errors.

## Change Summary
- Updated the preview close selector to exclude Toastify close buttons.

## Files Updated
- `ecm-frontend/e2e/search-view.spec.ts`

## Notes
- This is a test-stability fix only; no production code changes.
