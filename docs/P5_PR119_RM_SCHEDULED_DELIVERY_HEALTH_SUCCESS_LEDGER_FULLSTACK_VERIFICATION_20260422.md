# P5 PR-119 RM Scheduled Delivery Health Success Ledger Full-Stack Verification

## Scope Verified

- real-browser verification for the `Last 24h success` operator path on `Scheduled Delivery Health`
- verified on the live stack:
  - a real manual delivery increments the health-success signal
  - clicking `Last 24h success` drives the page-level `Preset Delivery Ledger`
  - the ledger shows active filter chips and the delivered file row
  - clearing the ledger filters restores the broader operator flow
- the existing `Scheduled presets` health drilldown, ledger export, zero-match recovery, and summary-only full-stack smoke remain green in the same spec

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

`PR-119` is accepted as the first real-stack smoke for the scheduled-delivery-health success signal drilling into the page-level preset delivery ledger.
