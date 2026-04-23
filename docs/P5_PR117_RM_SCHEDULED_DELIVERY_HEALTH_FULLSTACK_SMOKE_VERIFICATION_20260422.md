# P5 PR-117 RM Scheduled Delivery Health Full-Stack Smoke Verification

## Scope Verified

- real-browser verification for page-level `Scheduled Delivery Health`
- verified on the live stack:
  - scheduled-delivery telemetry renders after a real preset schedule + delivery flow
  - the `Scheduled presets` health drilldown works
  - the preset table enters its scheduled-filter state
  - the scheduled preset stays visible
  - an unscheduled control preset is filtered out
- existing ledger operator smoke and summary-only schedule smoke remain green in the same spec

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

`PR-117` is accepted as the first real-stack smoke for page-level scheduled-delivery telemetry and the scheduled-presets health drilldown on top of the shipped preset schedule/export/ledger chain.
