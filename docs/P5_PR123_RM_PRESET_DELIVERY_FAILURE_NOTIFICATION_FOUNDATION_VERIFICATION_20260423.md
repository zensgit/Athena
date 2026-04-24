# P5 PR-123 RM Preset Delivery Failure Notification Foundation Verification

## Verified Surface

- backend direct notification path for failed scheduled preset deliveries
- notification inbox/activity formatting for `rm.report_preset.delivery.failed`
- no new API or migration drift

## Commands

### Backend targeted tests

```bash
cd ecm-core && ./mvnw -B -Dstyle.color=never test -Dtest=ActivityServiceTest,NotificationInboxServiceTest,RmReportPresetDeliveryServiceTest
```

Result:

- `BUILD SUCCESS`
- `Tests run: 36, Failures: 0, Errors: 0, Skipped: 0`

### Frontend targeted tests

```bash
cd ecm-frontend && CI=true npm test -- --watchAll=false --runInBand --runTestsByPath src/utils/siteActivityUtils.test.ts src/utils/notificationUtils.test.ts --forceExit
```

Result:

- `Test Suites: 2 passed, 2 total`
- `Tests: 15 passed, 15 total`

### Frontend build

```bash
cd ecm-frontend && npm run build
```

Result:

- build completed successfully

### Static whitespace check

```bash
git diff --check
```

Result:

- passed

## Additional Review Outcome

Parallel read-only review confirmed the tenant-scope behavior of this slice:

- `nodeId = deliveryFolderId` keeps the alert visible in tenant-scoped inbox filtering when that folder is under the current tenant root
- site-only activity feeds do not surface this row because they query by `siteId`, not inferred node membership

Those are accepted boundaries for `PR-123`.
