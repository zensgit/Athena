# P5 PR-144D RM Notification Preflight CI Portability Fix Verification

## CI Failure Evidence

Command:

```bash
gh api repos/zensgit/Athena/actions/jobs/73021392351/logs
```

Relevant output from GitHub Actions run `24935937705`:

```text
scripts/p5-rm-notification-closeout-preflight.sh: line 18: rg: command not found
p5_rm_notification_closeout_preflight: missing CI workflow wiring: Run RM notification closeout preflight
```

Conclusion:

- the fast preflight failed before lint/build/test because `rg` was not available on the runner
- `Backend Verify` was already green for the same run

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

Observed coverage after removing `rg`:

- workflow YAML parse passed
- CI workflow wiring contract passed through `awk`
- acceptance gate script syntax passed
- backend test contract passed through `find` and `grep`
- bare Playwright API `response.ok()` assertion scan passed through `grep -E`
- all four expected RM notification acceptance scenario titles were present in the spec
- Playwright discovery found all four expected scenario titles
- RM notification acceptance discovery found the expected 4 tagged tests
- `peopleService.test.ts` passed 7/7 tests
- `RecordsManagementPage.test.tsx` passed the 2 targeted rollback tests
- script completed with `p5_rm_notification_closeout_preflight: ok`

## Regression Checks

Backend test class negative check:

```bash
ACCEPTANCE_GATE_SCRIPT="${tmp_gate}" scripts/p5-rm-notification-closeout-preflight.sh
```

Result:

- deleting `NotificationInboxServiceTest` from a temporary gate script failed as expected with status `1`

Acceptance scenario title negative check:

```bash
ACCEPTANCE_SPEC_FILE="${tmp_spec}" scripts/p5-rm-notification-closeout-preflight.sh
```

Result:

- deleting the disabled-failure-preference scenario title from a temporary spec failed as expected with status `1`

## Static Checks

Commands:

```bash
git diff --check
git diff --no-index --check /dev/null docs/P5_PR144B_RM_NOTIFICATION_ACCEPTANCE_SCENARIO_CONTRACT_DESIGN_20260426.md
git diff --no-index --check /dev/null docs/P5_PR144B_RM_NOTIFICATION_ACCEPTANCE_SCENARIO_CONTRACT_VERIFICATION_20260426.md
git diff --no-index --check /dev/null docs/P5_PR144C_RM_NOTIFICATION_BACKEND_TEST_CONTRACT_DESIGN_20260426.md
git diff --no-index --check /dev/null docs/P5_PR144C_RM_NOTIFICATION_BACKEND_TEST_CONTRACT_VERIFICATION_20260426.md
git diff --no-index --check /dev/null docs/P5_PR144D_RM_NOTIFICATION_PREFLIGHT_CI_PORTABILITY_FIX_DESIGN_20260426.md
git diff --no-index --check /dev/null docs/P5_PR144D_RM_NOTIFICATION_PREFLIGHT_CI_PORTABILITY_FIX_VERIFICATION_20260426.md
```

Result:

- passed with no whitespace errors

## Acceptance Status

This fix is ready for a new GitHub Actions run. Full notification-lane acceptance still requires `frontend_e2e_core` to pass the live `Run RM notification acceptance gate` step after this portability fix is pushed.
