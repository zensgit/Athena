# P5 PR-127 RM Preset Delivery Notification Preferences Full-Stack Verification

## Scope Added

- disabling success notifications does not stop scheduled delivery execution
- disabling success notifications suppresses the matching owner inbox row
- disabling failure notifications does not stop scheduled delivery execution
- disabling failure notifications suppresses the matching owner inbox row

## Frontend Unit Context

The page-level preference toggle behavior was already covered in `PR-126`.

Reference command:

```bash
cd ecm-frontend && CI=true npm test -- --watchAll=false --runInBand --runTestsByPath src/pages/RecordsManagementPage.test.tsx --testNamePattern="notification preferences" --forceExit
```

Result:

- not rerun for this test-only E2E slice

## Frontend Build

Command:

```bash
cd ecm-frontend && npm run build
```

Result:

- compiled successfully

## Playwright Spec Discovery

Command:

```bash
cd ecm-frontend && npx playwright test e2e/rm-report-preset-schedule.spec.ts --grep "disabled .* notification preference" --list
```

Result:

- discovered `RM disabled success notification preference suppresses inbox alert (full-stack)`
- discovered `RM disabled failure notification preference suppresses inbox alert (full-stack)`
- `Total: 2 tests in 1 file`

## Full-Stack Playwright

Command:

```bash
cd ecm-frontend && ECM_UI_URL=http://127.0.0.1:3000 ECM_API_URL=http://127.0.0.1:7700 KEYCLOAK_URL=http://127.0.0.1:8180 npx playwright test e2e/rm-report-preset-schedule.spec.ts --grep "disabled .* notification preference" --workers=1
```

Result:

- not run in this local session
- `http://127.0.0.1:3000`, `http://127.0.0.1:7700/actuator/health`, and `http://127.0.0.1:8180/health/ready` were not serving
- Docker access was unavailable, so the current session could not start the full stack or force a backend rebuild
- `docker info` failed with `permission denied while trying to connect to the docker API at unix:///Users/chouhua/.docker/run/docker.sock`

Required acceptance run:

- run the command above in a current full-stack environment
- expect both disabled-preference tests to pass
- if it fails, inspect preference cleanup first because both tests mutate owner preference rows

## Static Diff Check

Command:

```bash
git diff --check
```

Result:

- passed

## Notes

- this slice intentionally asserts absence by unique preset name in the notification API payload and `/notifications`
- unread count equality is not used as the primary signal because local environments may contain unrelated notification rows
- the new disabled-preference tests use public APIs plus a short due-soon cron instead of direct PostgreSQL mutation
