# P5 PR-124 RM Preset Delivery Failure Notification Full-Stack Smoke Verification

## Verified Surface

- admin trigger for due scheduled preset deliveries
- scheduled failure -> direct owner inbox notification
- notification row formatting and RM drilldown in a real browser flow

## Commands

### Backend targeted tests

```bash
cd ecm-core && ./mvnw -B -Dstyle.color=never test -Dtest=RmReportPresetControllerTest,ActivityServiceTest,NotificationInboxServiceTest,RmReportPresetDeliveryServiceTest
```

Result:

- `BUILD SUCCESS`
- `Tests run: 44, Failures: 0, Errors: 0, Skipped: 0`

### Live backend readiness and trigger probe

```bash
curl -fsS http://127.0.0.1:7700/actuator/health
curl -s -X POST http://127.0.0.1:7700/api/v1/records/report-presets/run-scheduled-deliveries ...
```

Result:

- `/actuator/health` returned `{"status":"UP"}`
- trigger endpoint returned `200` with JSON payload like:
  - `{"processedCount":0,"generatedAt":"..."}`

### Full-stack Playwright

```bash
cd ecm-frontend && ECM_UI_URL=http://127.0.0.1:3000 ECM_API_URL=http://127.0.0.1:7700 KEYCLOAK_URL=http://127.0.0.1:8180 npx playwright test e2e/rm-report-preset-schedule.spec.ts --grep "creates inbox notification" --workers=1
```

Result:

- `1 passed`

### Static whitespace check

```bash
git diff --check
```

Result:

- passed

## Notes

- The first probe against `:7700` returned `405`, confirming the container was still running an older backend image.
- The authoritative verification was taken only after rebuilding and recreating `ecm-core`.
