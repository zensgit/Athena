# Search + Preview UI P5 — Verification

Date: 2026-02-06

## Automated Tests
- `cd ecm-frontend && npm run lint`

## Manual Verification Checklist
1. **Score chip**
   - Run a search and confirm each result shows a `Score` chip when scores are available.

2. **Preview queue summary**
   - Trigger preview retries so queue status is populated.
   - Confirm the summary line shows the count and next retry time.
