# Mail Automation P21 — Verification

Date: 2026-02-06

## Automated Tests
- `cd ecm-frontend && npm run lint`
  - Result: pass

## Manual Verification Checklist
1. Open Mail Automation and set diagnostics filters.
2. Confirm URL query includes diagnostics keys (`dAccount/dRule/dStatus/dSubject/dFrom/dTo`).
3. Refresh page and confirm filters are restored from URL.
4. Open the same URL in a new tab and confirm the same filter state is restored.
5. Clear filters and confirm corresponding query parameters are removed.
