# Audit + Preview UI P4 — Verification

Date: 2026-02-06

## Automated Tests
- `cd ecm-frontend && npm run lint`

## Manual Verification Checklist
1. **Audit event labels**
   - Admin Dashboard → Audit logs.
   - Confirm event types render in title case (e.g., `NODE_CREATE` → `Node Create`).
   - Event type filter dropdown shows title-case labels.

2. **Preview queue tooltip details**
   - Search results page → queue a preview for a document.
   - Hover the preview status chip; tooltip should include attempts/next retry if available.
