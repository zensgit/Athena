# Preview P12 — Verification

Date: 2026-02-06

## Automated Tests
- `cd ecm-frontend && npm run lint`
  - Result: pass

## Manual Verification Checklist
1. In search results, ensure there are documents with `FAILED` preview status.
2. Confirm `Failed reasons (current page)` appears under preview controls.
3. Click `Retry this reason` on one bucket and confirm queue toast appears.
4. Click `Force rebuild` on one bucket and confirm queue toast appears.
