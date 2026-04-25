# P5 PR-144B RM Notification Acceptance Scenario Contract Verification

## Syntax Check

Command:

```bash
bash -n scripts/p5-rm-notification-closeout-preflight.sh
```

Result:

- passed

## Positive Preflight

Command:

```bash
scripts/p5-rm-notification-closeout-preflight.sh
```

Result:

- passed

Observed coverage:

- workflow YAML parse passed
- CI workflow wiring contract passed
- acceptance gate script syntax passed
- bare Playwright API `response.ok()` assertion scan found no violations
- all four expected RM notification acceptance scenario titles were present in the spec
- Playwright discovery found all four expected scenario titles
- RM notification acceptance discovery found the expected 4 tagged tests
- `peopleService.test.ts` passed 7/7 tests
- `RecordsManagementPage.test.tsx` passed the 2 targeted rollback tests
- script completed with `p5_rm_notification_closeout_preflight: ok`

## Negative Scenario Title Test

Command:

```bash
tmp_spec="$(mktemp)" \
  && grep -v 'RM disabled failure notification preference suppresses inbox alert @rm-notification-acceptance' \
    ecm-frontend/e2e/rm-report-preset-schedule.spec.ts > "${tmp_spec}" \
  && ACCEPTANCE_SPEC_FILE="${tmp_spec}" scripts/p5-rm-notification-closeout-preflight.sh
```

Expected result:

- failure before Playwright discovery and frontend unit tests

Observed result:

```text
p5_rm_notification_closeout_preflight: missing acceptance scenario title: RM disabled failure notification preference suppresses inbox alert @rm-notification-acceptance in /tmp/...
```

The command exited with status `1`, which is the intended fail-fast behavior.

## Acceptance Status

This is a local preflight hardening slice only. Full notification-lane acceptance still requires GitHub Actions `frontend_e2e_core` to pass the `Run RM notification acceptance gate` step on the pushed commit.
