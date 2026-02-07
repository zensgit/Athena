# Mail Automation P18 — Verification

Date: 2026-02-06

## Automated Tests
- `cd ecm-frontend && npm run lint`
  - Result: pass

## Manual Verification Checklist
1. Open Mail Automation -> Recent Mail Activity.
2. Click `Last 24h` and verify `Processed from/to` are auto-filled.
3. Click `Last 7d` / `Last 30d` and verify values update accordingly.
4. Confirm diagnostics results refresh under each preset.
5. Click `Clear time` and verify both datetime fields are cleared.
