# Phase 12 - Mail Automation Failure Insights

## Summary
- Added a failure insights panel to surface recent mail ingestion errors.
- Visualized last-24-hour error trend and grouped top error reasons.
- Highlighted impacted accounts to guide retry decisions.

## Implementation Details
- Aggregates error counts from recent diagnostics into 24 hourly buckets.
- Groups top error reasons (first-line normalization) with counts.
- Shows error rate, processed volume, and impacted account chips.
- Trend bars scale to max bucket count for quick visual scan.

## Files Changed
- `ecm-frontend/src/pages/MailAutomationPage.tsx`
- `ecm-frontend/e2e/mail-automation.spec.ts`

