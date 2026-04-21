# P5 PR-93: RM Report Preset Scheduled Delivery Foundation Verification

## Implemented

- added schedule metadata to `rm_report_presets`
- added `rm_report_preset_executions` ledger table
- added backend delivery service with:
  - schedule update/get
  - manual deliver-now
  - scheduled due-run processing
  - execution ledger persistence
- added preset controller endpoints for schedule status/update, manual deliver,
  and execution history

## Tests

Ran:

```bash
cd ecm-core
./mvnw -B -Dstyle.color=never test -Dtest=RmReportPresetControllerTest,RmReportPresetDeliveryServiceTest,RmReportPresetServiceTest
```

Result:

- `BUILD SUCCESS`
- `Tests run: 21, Failures: 0, Errors: 0, Skipped: 0`

Covered:

- enabling schedule for a supported preset kind computes `nextRunAt`
- summary-only preset kinds are rejected for scheduling
- manual deliver uploads CSV and records success execution
- scheduled runner executes due presets and advances `nextRunAt`
- controller endpoints return schedule, deliver, and execution payloads

## Static checks

Ran:

```bash
git diff --check
```

Result:

- passed

## Residual limits

- no frontend schedule UI yet
- no email delivery channel yet
- no restore/undelete for execution ledger
- scheduled delivery is CSV-only and limited to report kinds
