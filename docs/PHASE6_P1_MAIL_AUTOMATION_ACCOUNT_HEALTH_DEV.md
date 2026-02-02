# Phase 6 P1 Mail Automation Account Health - Development

## Summary
Add an account health summary to the Mail Automation page to surface fetch status, OAuth readiness, and staleness signals at a glance.

## Scope
- Summarize totals (total, enabled, disabled).
- Summarize fetch status (success, error, other, never fetched, stale).
- Summarize OAuth readiness (OAuth accounts, connected, not connected, env missing).
- Show latest fetch timestamp.

## Implementation
- Derived `accountHealth` via `useMemo` from the account list.
- Added an Account health chip stack above the Mail Accounts table.
- Added a staleness heuristic based on 2x poll interval per account.

## Files Changed
- `ecm-frontend/src/pages/MailAutomationPage.tsx`
