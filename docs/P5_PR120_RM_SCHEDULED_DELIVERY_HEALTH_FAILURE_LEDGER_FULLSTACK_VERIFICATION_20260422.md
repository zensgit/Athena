# P5 PR-120 RM Scheduled Delivery Health Failure Ledger Full-Stack Verification

## Scope Verified

- real-browser verification for the `Last 24h failed` operator path on `Scheduled Delivery Health`
- verified on the live stack:
  - a real failed delivery increments the health-failure signal
  - clicking `Last 24h failed` drives the page-level `Preset Delivery Ledger`
  - the ledger shows:
    - `Active ledger filters`
    - `Result: Failed`
    - `From:` / `To:`
  - ledger CSV export goes out with `status=FAILED`
- the existing success-path and summary-only full-stack smoke remain green in the same spec

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
Running 3 tests using 1 worker
  ✓ RM report preset schedule can be configured from Records Management (full-stack)
  ✓ RM summary-only preset can be exported and scheduled from Records Management (full-stack)
  ✓ RM failed preset delivery is surfaced through scheduled delivery health (full-stack)

  3 passed
```

## Static Check

```bash
git diff --check
```

Result:

- passed

## Conclusion

`PR-120` is accepted as the first real-stack smoke for the scheduled-delivery-health failure signal drilling into the page-level preset delivery ledger and export flow. The same full-spec rerun also keeps the existing success-path and summary-only preset full-stack smoke green on the current live stack.
