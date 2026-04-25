# P5 Phase 5 Mocked Regression Gate — Systemic Investigation

## Date
2026-04-26

## Scope

Investigation slice. Identifies the systemic cause behind the
chronic Phase 5 Mocked Regression Gate cancellation that has been
flagged in session memory as a separate-track concern across many
turns.

No code changes this slice. The fix is a separate follow-up; this
report establishes the root cause and the smallest viable fix path
so the follow-up can land cleanly.

## Why now

The Phase 5 Mocked gate has reported `cancelled` (30-min job timeout)
on every CI run for many weeks. Memory entry
`feedback_es_facet_aggregation_race.md` correctly flagged the
`search-preview-status:235` flake but that's *one* of the affected
tests — the broader gate cancellation has remained unexplained,
and memory entry `feedback_local_is_not_ci_verification.md` warned
against patching with retry-window tweaks. PR-146 is verifying in
parallel and is independent of this investigation, so this turn is
the right moment to do the systemic look.

## Evidence base

Source: Phase 5 Mocked job log on run `24935824097`
(commit `122c9ca`). Same pattern observed on `24899203627`,
`24811438908`, and earlier runs going back weeks.

### Failing tests (uniform timeouts, retry 3×)

| Spec | Line | Failure timeout | Retries |
|------|------|-----------------|---------|
| `admin-audit-filter-export.mock.spec.ts` | 6 | 32.5s × 3 | × 3 |
| `app-error-boundary-chunk-load-recovery.mock.spec.ts` | 3 | 1.1m × 3 | × 3 |
| `app-error-boundary-chunk-load-recovery.mock.spec.ts` | 27 | 1.1m × 3 | × 3 |
| `app-error-boundary-noise-filter.mock.spec.ts` | 3 | 1.1m × 3 | × 3 |
| `app-error-boundary-noise-filter.mock.spec.ts` | 18 | 1.1m × 3 | × 3 |
| `app-error-boundary-recovery.mock.spec.ts` | 5 | 1.1m × 3 | × 3 |
| `bootstrap-startup-fallback.mock.spec.ts` | 6, 56, 99 | 1.1m × 3 | × 3 |

≥ 9 failing test cases × 3 retries × ~1.1m ≈ 30 min — exhausts the
job timeout.

### Passing tests in the same suite

| Spec | Time | Setup |
|------|------|-------|
| `admin-preview-diagnostics.mock.spec.ts:5` | 1.7m (slow but passes) | Goes through `/admin/preview-diagnostics` directly |
| `auth-boot-watchdog-recovery.mock.spec.ts:6` | 2.7s | Direct route mock |
| `auth-session-recovery.mock.spec.ts:112,175` | 1.4s, 1.5s | **`seedBypassSessionE2E`** before goto |
| `auth-storage-restricted-recovery.mock.spec.ts:11` | 1.4s | Direct mock |

## Root cause

Failing tests all start with:
```ts
await page.goto('/login', { waitUntil: 'domcontentloaded' });
await expect(page.getByText('Sign in with your organization account'))
    .toBeVisible({ timeout: 60_000 });
```

Or otherwise hit the unauth bootstrap path. They do **not** call
`seedBypassSessionE2E`, which means the app tries to talk to
Keycloak to determine session state.

The Phase 5 Mocked CI environment runs from
`scripts/phase5-regression.sh` line 1006:

```bash
npx serve -s build -l 0 ...
```

This is a static-only serve. **No backend, no Keycloak, no PostgreSQL,
no anything else**. When the app's auth boot logic talks to Keycloak,
the request hangs (or fails in a way the auth-boot-watchdog cannot
recover from quickly enough). The expect for "Sign in with your
organization account" never resolves within the 60s test budget,
the test fails, retry rerun is identical, retries × 3, ×9+ tests,
30-min timeout, gate cancelled.

The passing auth-* tests call `seedBypassSessionE2E` before any
`goto`, which sets `ecm_e2e_bypass=1` in localStorage via
`addInitScript`. The app sees that flag at boot and skips Keycloak
entirely. /login renders fast. Tests pass.

This is the same class of issue called out in memory entry
`feedback_local_is_not_ci_verification.md`: a test passing locally
(against a dev server with the full stack reachable) is not the
same as it passing on CI's static `serve -s build`.

## Why retry-window tweaks won't fix it

PR-93's earlier attempt added a 5-attempt retry loop on
`bootstrap-startup-fallback`. The retries don't help because the
underlying Keycloak call still hangs — bumping the retry budget
just delays the deterministic failure. Memory entry already pinned
this conclusion. The tests that fail share the same shape (wait for
`/login` text under unauth boot) and respond identically to retries.

## Fix path (for follow-up slice)

Two viable shapes:

### Option A — per-test bypass + selective unauth coverage (smallest)

For each failing test, decide:

- **Does the test need to exercise the real unauth /login render?**
  If NO, add `seedBypassSessionE2E(page, 'admin', 'e2e-token')` at
  the top so the bootstrap is skipped. Tests that just need to be
  on a route to test other behavior fall in this bucket.
- **If YES** (e.g., `app-error-boundary-recovery` deliberately tests
  the "back to login" recovery flow), the test needs a Keycloak
  mock — Playwright `page.route('**/realms/**', ...)` returning
  immediate `401` or `200` JSON. That path keeps the test's
  semantic intent.

Effort: ~30 min per test × 9 tests ≈ 0.5 day.

### Option B — global Keycloak shim in the static-serve build

Add a `phase5-regression`-only `init-script.js` (loaded by
`serve -s build`'s static index) that stubs `window.fetch` for
Keycloak well-known + token endpoints with immediate `401`
responses. Every test in the suite gets the fix automatically.

Effort: ~1-2 days. Higher blast radius — could mask real auth
regressions.

**Recommended**: Option A. Per-test bypass keeps each test's intent
clear and avoids global suite-wide shims. Splits the work into
small, reviewable commits.

## Proof of concept (NOT shipped this turn)

For `app-error-boundary-noise-filter.mock.spec.ts:3`
("ignores ResizeObserver global error noise"), the test does not
need the real unauth /login bootstrap — the noise filter is a
global behavior. Adding one line:

```ts
import { seedBypassSessionE2E } from './helpers/login';

test('App error boundary: ignores ResizeObserver global error noise (mocked)',
  async ({ page }) => {
    test.setTimeout(120_000);
    await seedBypassSessionE2E(page, 'admin', 'e2e-token');
    // ... rest unchanged ...
});
```

…would skip the Keycloak-hang path. Same pattern applies to most
of the failing tests except `app-error-boundary-recovery` and
`bootstrap-startup-fallback:99` which do exercise the unauth flow.

## Why no fix this turn

PR-146 is in flight, verifying the notification lane runtime fix.
Adding cross-test changes to the same push would muddy the verdict
on PR-146 vs. the Phase 5 fix. Memory project entry
`project_rm_preset_delivery_closeout.md` makes the boundary-keeping
rule explicit. Phase 5 Mocked is not blocking the notification
lane; it's not blocking the email lane. The cost of waiting one
turn for a clean verdict is small.

## Recommended sequencing

1. Wait for PR-146 (`ae8c735`) CI to land its verdict on the
   notification lane.
2. **Whether or not PR-146 goes green**, file PR-148+ as a series of
   per-test bypass / Keycloak mock additions following Option A.
   Each test-or-spec-level change is a small reviewable commit.
3. After Phase 5 Mocked goes green for the first time on this repo,
   add a feedback memory entry retiring the
   `feedback_es_facet_aggregation_race.md` warning's gate-level
   implication (the specific facet race may still flake, but the
   gate-level systemic issue would be closed).

## Verification of this analysis

| Claim | Evidence |
|-------|----------|
| Failing tests all hit /login bootstrap path | grep of failing spec files all show `page.goto('/login', ...)` or hit the unauth boot |
| Passing auth-* tests bypass /login | grep of passing spec files all show `seedBypassSessionE2E(...)` before any `goto` |
| Static serve has no Keycloak | `scripts/phase5-regression.sh:1006` — `npx serve -s build`, no docker compose |
| 60s timeout in failing tests | grep of `toBeVisible({ timeout: 60_000 })` in failing spec files |
| Retry doesn't change outcome | All retry #1 / retry #2 columns show same pass/fail with similar timing |
| Job-level cancellation = 30 min | `.github/workflows/ci.yml` `frontend_e2e_phase5_mocked.timeout-minutes: 30` |

## Files Changed

None. This is a docs-only investigation.

## Memory entry update

After follow-up slice fixes the failing tests, update memory:

- Demote `feedback_es_facet_aggregation_race.md` — the broader
  gate-level concern is resolved; the specific test flake remains
  documented but is no longer gate-blocking.

## Non-goals

- Did not modify any test in this slice
- Did not add a global Keycloak shim
- Did not change the Phase 5 regression script
- Did not investigate the slow-but-passing
  `admin-preview-diagnostics:5` (1.7m); it passes, so it's not
  contributing to the cancellation budget meaningfully.
- Did not investigate `search-preview-status.spec.ts:235` —
  separate test in the *Frontend E2E Core Gate*, not Phase 5
  Mocked, already documented in
  `feedback_es_facet_aggregation_race.md`.
