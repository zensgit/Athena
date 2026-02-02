# Phase 6 P1 Mail Automation Fetch Summary - Development

## Summary
Add a fetch/diagnostics summary panel to surface the latest Trigger Fetch run and provide a quick refresh action.

## Scope
- Track last Trigger Fetch summary and timestamp.
- Display key counters (accounts, matched, processed, skipped, errors, duration).
- Provide a quick Refresh Status action in the summary panel.

## Implementation
- Added `lastFetchSummary` and `lastFetchAt` state tied to Trigger Fetch.
- Rendered a summary chip stack with key metrics and a timestamp.
- Added a refresh button that reuses the existing status refresh handler.

## Files Changed
- `ecm-frontend/src/pages/MailAutomationPage.tsx`
