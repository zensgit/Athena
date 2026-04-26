# P5 PR-162 — Phase 5 Mocked true-green closeout

## Date
2026-04-26

## Scope

Close the remaining Phase 5 Mocked Regression Gate gap after PR-161.

PR-161 made the gate script pass with two conservative `test.fixme`
entries still present. PR-162 validates the new no-session Keycloak
mock against those two subjects, re-enables them, and hardens the
admin audit mocked spec that was still sensitive to new dashboard
panels and MUI select timing.

No production frontend code, backend code, migrations, or CI workflow
files changed in this closeout. The change is limited to mocked E2E
specs and this verification document.

## Design

### 1. Re-enable the two PR-160 login-path fixmes

The current `mockKeycloakUnreachable` helper sets:

- `ecm_e2e_bypass=1`
- no token
- no user

That makes `authService.init()` return unauthenticated immediately
without importing `keycloak-js`. This is enough for the two previously
fixme'd tests:

- `route-fallback-no-blank.mock.spec.ts` unauth unknown route to `/login`
- `startup-visibility-sla.mock.spec.ts` `/login` visible SLA

The tests now run normally instead of being skipped.

### 2. Stabilize admin audit async status selection

The admin dashboard now includes multiple async task surfaces. The
audit spec therefore mocks adjacent task-center endpoints so unrelated
panels do not create latent 404 noise:

- `/nodes/download/batch-async/summary`
- `/nodes/download/batch-async`
- `/analytics/async-governance/tasks`

The audit async status selector is also driven through the specific
combobox with `aria-label="Audit async task status filter"`. Menu
items are selected by focusing the current open listbox option and
pressing Enter. This avoids Toastify pointer interception while still
asserting the real result:

- combobox text changes to the selected label
- the mocked list endpoint observes the expected `status` query

### 3. Keep the gate honest

The final gate result is not "passed with skips". It is:

- 30 tests executed
- 30 passed
- 0 skipped
- 0 recovery guard warnings

This supersedes the conservative PR-160/PR-161 posture that still had
two skipped login-path tests.

## Files Changed

| File | Purpose |
|------|---------|
| `ecm-frontend/e2e/admin-audit-filter-export.mock.spec.ts` | Mock adjacent async task panels and make audit status select deterministic. |
| `ecm-frontend/e2e/route-fallback-no-blank.mock.spec.ts` | Convert unauth route fallback from `test.fixme` back to active `test`. |
| `ecm-frontend/e2e/startup-visibility-sla.mock.spec.ts` | Convert login SLA from `test.fixme` back to active `test`. |
| `docs/P5_PR162_PHASE5_MOCKED_TRUE_GREEN_CLOSEOUT_DEV_VERIFICATION_20260426.md` | Design and verification record. |

## Verification

### Static checks

```bash
git diff --check
```

Result: passed.

```bash
PHASE5_VALIDATE_RECOVERY_REGISTRY_ONLY=1 scripts/phase5-regression.sh
```

Result:

- expected events: 24
- observed markers in specs: 24
- missing from events file: 0
- stale events file entries: 0
- registry matches spec markers

### Targeted E2E

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 npx playwright test \
  e2e/admin-audit-filter-export.mock.spec.ts \
  --project=chromium --workers=1 --retries=0
```

Result: 1 passed.

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 npx playwright test \
  e2e/route-fallback-no-blank.mock.spec.ts \
  e2e/startup-visibility-sla.mock.spec.ts \
  --project=chromium --workers=1 --retries=0
```

Result: 4 passed.

Observed recovery/SLA markers:

- `recovery_event:route_fallback_unauth_login_visible`
- `recovery_event:route_fallback_auth_browse_visible`
- `startup_sla:login_visible_ms=823:threshold_ms=12000`
- `startup_sla:browse_visible_ms=925:threshold_ms=15000`

### Full Phase 5 Mocked Gate

```bash
CI=false scripts/phase5-regression.sh
```

Result:

- 30 passed
- 0 skipped
- startup SLA warning count: 0
- startup SLA drift warning count: 0
- recovery guard: OK all expected recovery events observed
- recovery guard warning count: 0

Observed final SLA samples:

- `startup_sla:login_visible_ms=1004:threshold_ms=12000`
- `startup_sla:browse_visible_ms=945:threshold_ms=15000`

## Residual Risk

- Backend tests were not rerun because this closeout does not touch
  backend code. CI remains authoritative for full cross-job status.
- The admin preview diagnostics spec remains the longest Phase 5
  mocked test at about 72 seconds. This is not a correctness blocker,
  but it is the next obvious duration hotspot.
- `.env` and `.claude/` were present in the worktree and intentionally
  left unstaged.

## Outcome

Phase 5 Mocked Regression Gate is now locally true-green rather than
green-by-skip. The prior PR-160 fixme posture is superseded by active
login-path coverage and a full 30/30 pass.
