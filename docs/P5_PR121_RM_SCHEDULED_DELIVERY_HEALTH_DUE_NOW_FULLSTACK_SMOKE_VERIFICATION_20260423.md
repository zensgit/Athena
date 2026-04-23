# P5 PR-121 RM Scheduled Delivery Health Due Now Full-Stack Smoke Verification

## Scope Verified

- page-level `Refresh` now reloads preset list, scheduled-delivery health, and preset ledger together after external schedule-state changes
- real-browser verification for the `Due now` operator path on `Scheduled Delivery Health`
- verified on the live stack:
  - a scheduled preset whose `next_run_at` is forced into the past is counted by the health card as due now
  - clicking `Due now` drives the page-level `Saved RM Report Presets` table into the `dueNow` filter
  - the due-now preset remains visible
  - a future scheduled control preset is filtered out
- the existing success-path, summary-only, and failure-path full-stack smoke remain green in the same spec

## Environment

- frontend: current worktree dev server on `http://127.0.0.1:3000`
- backend: current docker `ecm-core` on `http://127.0.0.1:7700`
- keycloak: `http://127.0.0.1:8180`
- postgres: current docker `athena-postgres-1`

## Command

```bash
cd ecm-frontend
ECM_UI_URL=http://127.0.0.1:3000 ECM_API_URL=http://127.0.0.1:7700 KEYCLOAK_URL=http://127.0.0.1:8180 npx playwright test e2e/rm-report-preset-schedule.spec.ts --workers=1
```

## Result

```text
Targeted unit regression:
PASS src/pages/RecordsManagementPage.test.tsx
  ✓ renders the scheduled delivery health card with telemetry counts
  ✓ filters saved presets from scheduled delivery health drilldowns
  ✓ refreshes scheduled delivery health and preset delivery surfaces when Refresh is clicked

Full-stack Playwright:
Running 4 tests using 1 worker
  ✓ RM report preset schedule can be configured from Records Management (full-stack)
  ✓ RM summary-only preset can be exported and scheduled from Records Management (full-stack)
  ✓ RM failed preset delivery is surfaced through scheduled delivery health (full-stack)
  ✓ RM due-now preset is surfaced through scheduled delivery health (full-stack)

  4 passed
```

## Static Check

```bash
git diff --check
```

Result:

- passed

## Conclusion

`PR-121` is accepted as the first real-stack smoke for the scheduled-delivery-health due-now signal drilling into the page-level preset table filter, and it hardens page-level `Refresh` so the preset, health, and ledger delivery surfaces stay in sync.
