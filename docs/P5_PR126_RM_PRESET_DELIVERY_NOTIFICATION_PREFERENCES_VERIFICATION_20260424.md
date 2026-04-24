# P5 PR-126 RM Preset Delivery Notification Preferences Verification

## Scope Verified

- scheduled delivery success notifications now honor an owner preference
- scheduled delivery failure notifications now honor an owner preference
- RM page exposes two toggles backed by the existing People preferences API
- no new endpoint, table, or migration was introduced

## Tests

### Backend

```bash
cd ecm-core && ./mvnw -B -Dstyle.color=never test -Dtest=RmReportPresetDeliveryServiceTest
```

Result:

- `BUILD SUCCESS`
- `Tests run: 14, Failures: 0, Errors: 0, Skipped: 0`

### Frontend

```bash
cd ecm-frontend && CI=true npm test -- --watchAll=false --runInBand --runTestsByPath src/pages/RecordsManagementPage.test.tsx --testNamePattern="notification preferences|scheduled delivery health" --forceExit
```

Result:

- `PASS src/pages/RecordsManagementPage.test.tsx`
- `5 passed, 74 skipped, 79 total`

### Frontend Build

```bash
cd ecm-frontend && npm run build
```

Result:

- compiled successfully

### Static Diff Check

```bash
git diff --check
```

Result:

- passed

## Behavioral Checks

- missing preference values preserve the current shipped notification semantics
- disabling success alerts suppresses only `rm.report_preset.delivery.succeeded`
- disabling failure alerts suppresses only `rm.report_preset.delivery.failed`
- manual `Deliver now` remains outside this preference lane because direct inbox alerts are still scheduled-only

## Notes

- this slice intentionally reuses the existing People preference storage instead of adding a dedicated RM preference endpoint
- follow-up full-stack coverage for disabled preferences is tracked in `P5_PR127_RM_PRESET_DELIVERY_NOTIFICATION_PREFERENCES_FULLSTACK_VERIFICATION_20260424.md`
