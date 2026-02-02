# Phase 3 (P1) - Advanced Search Preview Status Badges (Verification)

Date: 2026-02-01

## Test Summary
- Playwright (baseline UI smoke): `cd ecm-frontend && npx playwright test e2e/p1-smoke.spec.ts`
  - Result: ✅ 2 passed

## Suggested Manual Checks
1. Open Advanced Search and locate a document with non-ready preview status.
2. Confirm a preview status chip appears in the result row.
3. If failed, hover to see the failure reason tooltip.
