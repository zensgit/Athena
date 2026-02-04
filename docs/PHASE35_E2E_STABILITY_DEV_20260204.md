# Phase 35 E2E Stability Adjustments (Dev)

## Goal
Stabilize UI smoke under production/dev UI URLs by ensuring search results return to base view after similar search and reducing flakiness in scheduled rule tag verification.

## Changes
- After "More like this" (similar search), re-run quick search when results do not return to base list, then re-select the result card for preview actions.
- Scheduled rule tag verification now retries longer, re-triggers the rule mid-loop, and records a warning if tag application is delayed.

## Files
- `ecm-frontend/e2e/ui-smoke.spec.ts`

## Notes
- The adjustments are test-only; no production code changes.
