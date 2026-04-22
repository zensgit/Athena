# P5 PR-116 RM Preset Delivery Ledger Full-Stack Smoke Verification

## Scope Verified

- real-browser verification for page-level preset delivery ledger operator behavior
- verified on the live stack:
  - delivered preset execution appears in `Preset Delivery Ledger`
  - preset-scoped ledger filtering works
  - ledger CSV export works for the selected preset
  - zero-match state appears when the filter window excludes the execution
  - `Show all deliveries` restores the ledger
- existing summary-only full-stack preset schedule/export flow remains green in the same spec

## Environment

- frontend: current worktree dev server on `http://127.0.0.1:3000`
- backend: current docker `ecm-core` on `http://127.0.0.1:7700`
- keycloak: `http://127.0.0.1:8180`

## Command

```bash
cd ecm-frontend
ECM_UI_URL=http://127.0.0.1:3000 ECM_API_URL=http://127.0.0.1:7700 KEYCLOAK_URL=http://127.0.0.1:8180 npx playwright test e2e/rm-report-preset-schedule.spec.ts --workers=1
```

## Result

```text
Running 2 tests using 1 worker
  ✓ RM report preset schedule can be configured from Records Management (full-stack)
  ✓ RM summary-only preset can be exported and scheduled from Records Management (full-stack)
  2 passed
```

## Static Check

```bash
git diff --check
```

Result:

- passed

## Conclusion

`PR-116` is accepted as the first real-stack operator smoke for page-level preset delivery ledger actions on top of the shipped preset scheduled-delivery chain.
