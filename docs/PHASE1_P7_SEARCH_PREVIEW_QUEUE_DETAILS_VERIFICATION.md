# Search Preview Queue P7 — Verification

Date: 2026-02-06

## Automated Tests
- `cd ecm-frontend && npm run lint`
  - Result: pass

## Manual Verification Checklist
1. Queue previews for items on the current page (retry or force rebuild).
2. Confirm a "Queued preview details" list appears under the preview status filters.
3. Verify each entry shows name, status, attempts, and next retry time (if provided).
