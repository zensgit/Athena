# Mail Automation P25 — Verification

Date: 2026-02-06

## Automated Tests
- `cd ecm-frontend && npm run lint`
  - Result: pass
- `cd ecm-core && mvn -DskipTests compile`
  - Result: pass

## Manual Verification Checklist
1. Open Mail Automation diagnostics filters.
2. Change `Sort by` and `Order`, confirm processed list order changes.
3. Refresh page and confirm sort/order restored from URL/local state.
4. Click `Export CSV` and verify CSV metadata includes `SortBy` and `SortOrder`.
5. Call diagnostics API with and without `sort/order`, confirm defaults remain backward-compatible.
