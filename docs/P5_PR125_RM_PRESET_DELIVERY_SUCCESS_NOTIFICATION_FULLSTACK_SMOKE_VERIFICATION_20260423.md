# P5 PR-125 RM Preset Delivery Success Notification Full-Stack Smoke Verification

## Verified Surface

- scheduled success -> direct owner inbox notification
- notification label/summary/link rendering for `rm.report_preset.delivery.succeeded`
- live `/notifications` drilldown to delivered node

## Commands

### Backend targeted tests

```bash
cd ecm-core && ./mvnw -B -Dstyle.color=never test -Dtest=RmReportPresetControllerTest,ActivityServiceTest,NotificationInboxServiceTest,RmReportPresetDeliveryServiceTest
```

Result:

- `BUILD SUCCESS`
- `Tests run: 45, Failures: 0, Errors: 0, Skipped: 0`

### Frontend targeted tests

```bash
cd ecm-frontend && CI=true npm test -- --watchAll=false --runInBand --runTestsByPath src/utils/siteActivityUtils.test.ts src/utils/notificationUtils.test.ts --forceExit
```

Result:

- `Test Suites: 2 passed, 2 total`
- `Tests: 18 passed, 18 total`

### Full-stack Playwright

```bash
cd ecm-frontend && ECM_UI_URL=http://127.0.0.1:3000 ECM_API_URL=http://127.0.0.1:7700 KEYCLOAK_URL=http://127.0.0.1:8180 npx playwright test e2e/rm-report-preset-schedule.spec.ts --grep "successful scheduled preset delivery creates inbox notification" --workers=1
```

Result:

- `1 passed`

### Static checks

```bash
cd ecm-frontend && npm run build
git diff --check
```

Result:

- build completed successfully
- whitespace check passed

## Review Note

Parallel read-only review confirmed one accepted boundary:

- success notifications still pass through the tenant visibility gate via `nodeId`
- if the delivered document is outside the current tenant root, the inbox entry will be filtered out in scoped tenant views

That is consistent with the current activity/notification visibility model.
