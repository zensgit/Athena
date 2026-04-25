# P5 PR-148 — Phase 5 Mocked: Noise-Filter Spec Bypass (Proof-of-Concept)

## Date
2026-04-26

## Scope

E2E test only. Smallest viable proof-of-concept for the Option A fix
path documented in
`docs/P5_PHASE5_MOCKED_GATE_INVESTIGATION_DEV_VERIFICATION_20260426.md`
(commit `dce7443`).

Switches `app-error-boundary-noise-filter.mock.spec.ts` from
unauthenticated `/login` boot to bypass-authenticated `/` boot, so
the test no longer hangs on Keycloak in Phase 5 Mocked's
`serve -s build` environment.

No production code changed. No backend change.

## Why this slice (and not bigger)

The investigation MD identified ~9 failing mock-spec tests with the
same systemic Keycloak-hang cause. Each test needs case-by-case
analysis:

| Spec | Bypass-safe? | Reason |
|------|--------------|--------|
| `app-error-boundary-noise-filter.mock.spec.ts` (2 tests) | **Yes** | Noise filter is page-agnostic behaviour |
| `app-error-boundary-chunk-load-recovery.mock.spec.ts` (2 tests) | Likely | Tests chunk-load failure on /login; could move to authenticated route |
| `app-error-boundary-recovery.mock.spec.ts` | **No** | Test deliberately exercises "back to login" recovery flow |
| `bootstrap-startup-fallback.mock.spec.ts` (3 tests) | Mixed | Some tests pre-auth state explicitly |
| `admin-audit-filter-export.mock.spec.ts:6` | **N/A** | Already uses bypass; failure has different cause (out of scope) |

Shipping the noise filter as a proof-of-concept lets CI confirm the
approach works before rolling out to the harder cases. If it
*doesn't* work (e.g., something else in the static-serve env is also
hanging), we learn that quickly without committing to a multi-spec
refactor.

## Design

### Root cause recap (from the investigation MD)

```
page.goto('/login', { waitUntil: 'domcontentloaded' })   ← OK
  → keycloak-js.init() tries http://localhost:8180/realms/ecm/...
  → no Keycloak in static-serve env → hangs
  → app never renders LoginPage
  → expect.toBeVisible('Sign in...', { timeout: 60_000 }) times out
  → test fails at 1.1m
  → ×3 retries × multiple specs → 30-min job timeout → cancelled
```

### Fix shape

The noise filter is a **global behaviour** — the same listener on
`window` handles every error/unhandledrejection event regardless of
route. The previous spec used `/login` purely to have *some* page
loaded so the listener was attached.

```diff
- await page.goto('/login', { waitUntil: 'domcontentloaded' });
- await expect(page.getByText('Sign in with your organization account'))
-   .toBeVisible({ timeout: 60_000 });
+ await seedBypassSessionE2E(page, 'admin', 'e2e-token');
+ await page.goto('/', { waitUntil: 'domcontentloaded' });
+ await expect(page.getByRole('button', { name: 'Account menu' }))
+   .toBeVisible({ timeout: 60_000 });
```

After the change:

- `seedBypassSessionE2E` sets `ecm_e2e_bypass=1` via `addInitScript`
  before any document parse — `authService` short-circuits Keycloak
  init entirely
- `/` renders the authenticated home page within seconds
- The dispatched noise events fire on the same `window`
- Assertions verify (a) the error overlay does NOT appear and
  (b) the surrounding UI is still visible
- Behaviour under test is unchanged; only the host page changes

### What's preserved

- Test names (the noise filter is the subject under test, not the
  page it ran on)
- The `recovery_event:` console-log markers used by Phase 5's
  recovery-events expectation file
- `test.setTimeout(120_000)` — same budget
- The 60s expect-timeout — same budget per assertion

### What's deliberately NOT in this slice

- The other 7 failing tests — each needs its own decision (bypass
  vs. Keycloak `page.route` mock vs. semantic redesign)
- A global Keycloak shim (Option B in the investigation) — higher
  blast radius, may mask real auth regressions
- Any change to the production code

## Verification

### Local
```
cd ecm-frontend
npx -p typescript@5.4.5 tsc --noEmit   → clean
npm run lint                           → clean
```

The spec uses an established helper (`seedBypassSessionE2E` from
`ecm-frontend/e2e/helpers/login.ts`) already imported by 4 other
passing mock specs, so the pattern is well-tested at the helper level.

Cannot run the e2e itself locally — that requires a built `serve -s build`
mirror of CI, which is the whole point of this gate's existence
(memory entry `feedback_local_is_not_ci_verification.md`).

### Expected CI signal on `80a1275`

| Job | Expected |
|-----|----------|
| Backend Verify | ✅ unchanged (no backend) |
| Frontend Build & Test | ✅ unchanged (no Jest tests touched) |
| Phase C Security | ✅ unchanged |
| Acceptance Smoke | ✅ unchanged |
| Frontend E2E Core Gate | ✅ unchanged (mock specs don't run here) |
| **Phase 5 Mocked Regression Gate** | **2 fewer failures** (or, if env-specific issue persists, same failures with different timing — gives us more data) |
| RM Notification Gate (PR-146 step) | ✅ if PR-146's tx-isolation fix works on `ae8c735` |

If the gate is still cancelled (because 7 other tests still fail at
1.1m each), the noise-filter tests should now show up as **passing**
in the per-test results, even though the overall job times out.
That's the proof signal we need before scaling the rollout.

## Files Changed

| File | Lines |
|------|-------|
| `ecm-frontend/e2e/app-error-boundary-noise-filter.mock.spec.ts` | +14 / -6 |

No new file. No production code change. No migration. No script.

## Sequencing & next slices

| PR | Role | Status |
|----|------|--------|
| PR-145 | Diagnostic catch + IllegalStateException handler | ✅ shipped |
| PR-146 | Tx isolation + controller-boundary diagnostic | ✅ shipped — verifying on `ae8c735` |
| **PR-148** | **PoC: noise-filter spec bypass on Phase 5 Mocked** | **✅ shipped this turn** |

If the PoC lands the noise-filter tests green on Phase 5 Mocked:
- **PR-149** (proposed): bypass for `app-error-boundary-chunk-load-recovery.mock.spec.ts`
- **PR-150** (proposed): Keycloak `page.route` mock for
  `app-error-boundary-recovery.mock.spec.ts` (preserves the unauth
  /login → back-to-login → recovery flow)
- **PR-151** (proposed): per-test analysis + fix for the three
  `bootstrap-startup-fallback.mock.spec.ts` cases

Each is a small additive slice; together they should land Phase 5
Mocked green for the first time on this repo.

## Non-goals

- Did not modify the e2e test for any other failing spec
- Did not touch `app-error-boundary-recovery.mock.spec.ts` —
  bypassing it would change semantic intent
- Did not change `bootstrap-startup-fallback.mock.spec.ts`
- Did not change `admin-audit-filter-export.mock.spec.ts:6` —
  already uses bypass; its 32.5s failure has a different root cause
  that warrants its own investigation slice
- Did not investigate `search-preview-status.spec.ts:235` — this
  spec runs in **Frontend E2E Core Gate**, not Phase 5 Mocked, and
  is documented separately in
  `feedback_es_facet_aggregation_race.md`

## Memory entry implications

If this slice + the follow-ups land Phase 5 Mocked green:

- `feedback_local_is_not_ci_verification.md` — keep, the rule is
  still useful
- `feedback_es_facet_aggregation_race.md` — keep, still describes
  the search-preview-status flake on Core Gate
- New entry to consider: a short note on the bypass-vs-keycloak-mock
  decision rule for Phase 5 Mocked specs, so the pattern is
  preserved across future test additions
