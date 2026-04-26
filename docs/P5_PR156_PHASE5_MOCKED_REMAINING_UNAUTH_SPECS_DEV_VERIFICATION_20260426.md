# P5 PR-156 — Phase 5 Mocked: Extend `mockKeycloakUnreachable` to Remaining Unauth-Flow Specs

## Date
2026-04-26

## Scope

Adds the (now-correct, per PR-155 abort behaviour) helper to the two
remaining Phase 5 Mocked specs that hang on Keycloak:

- `route-fallback-no-blank.mock.spec.ts:77` — "unknown route redirects
  unauthenticated users to login"
- `startup-visibility-sla.mock.spec.ts:88` — "login route visible
  under threshold"

E2E test changes only. No backend, no frontend production code, no
new helper.

## Why this slice

`8410eaf` and `3708ba8` Phase 5 Mocked verdicts confirmed:

- The job now **finishes within the 30-min budget** (first time on
  this repo) — PR-148/149/150/151's rollout returned enough time
- 4 specs still fail with the **same systemic Keycloak hang**:
  - `bootstrap-startup-fallback:19, :70` (PR-151 already added the
    helper; PR-155 fixed its abort behaviour — pending CI verdict)
  - `app-error-boundary-recovery:5` (PR-152 already added; same
    pending verdict)
  - `route-fallback-no-blank:77` (this commit)
  - `startup-visibility-sla:88` (this commit)

Both tests in this commit are textbook "unauth /login flow IS the
subject" — the test:

- Goes through the unauth boot path and waits for the unauth shell
- Has an authenticated counterpart in the same file that already
  uses `seedBypassSessionE2E` and passes

So the bypass approach is wrong; the Keycloak mock is the right
strategy per the decision rule in
`memory/feedback_phase5_mocked_keycloak_strategy.md`.

## Design

Two-line change per file:

```diff
+ import { mockKeycloakUnreachable } from './helpers/keycloakMock';
  ...
  test('Route fallback: unknown route redirects unauthenticated users ...', async ({ page }) => {
    test.setTimeout(120_000);
+   await mockKeycloakUnreachable(page);
    await page.goto('/definitely-not-a-real-route', { waitUntil: 'domcontentloaded' });
```

Same shape for `startup-visibility-sla:88`. The helper aborts every
`**/realms/**` request (per PR-155 abort fix), so:

- The auth-init AJAX call rejects fast → app falls into unauth state
- The `window.location.href = realms-auth-url` redirect doesn't
  navigate (browser sees connection failure)
- /login renders the local sign-in shell within seconds

The SLA test specifically benefits — its assertion `elapsedMs ≤
LOGIN_VISIBLE_SLA_MS` is meant to catch perf regressions. Without
this fix, it was catching environment limitations (no Keycloak in
static-serve), not real perf changes.

## Verification

### Local
- `npx -p typescript@5.4.5 tsc --noEmit` — clean
- `npm run lint` — clean

### Expected CI signal on `6200639`

| Job | Expected |
|-----|----------|
| Backend Verify | ✅ unchanged (no backend) |
| Frontend Build & Test | ✅ unchanged (no Jest tests touched) |
| Phase C Security | ✅ unchanged |
| Acceptance Smoke | ✅ unchanged |
| Frontend E2E Core Gate | ✅ — PR-154's locator fix already deterministic; no regression from this commit |
| **Phase 5 Mocked Regression Gate** | **2 more tests rescued**, plus the 4 (PR-151's `:19/:70`, PR-152's recovery) that should land green via PR-155's abort. So ~6 tests transition from red → green |

After `6200639` and `beca1cf` both land green, the only known
remaining Phase 5 Mocked failures are:

- `search-suggestions-save-search:4, :184` — ~1s failures, different
  cause (likely a DOM/timing assertion, not a Keycloak hang)
- `admin-audit-filter-export:6` — already uses bypass; 32.5s timeout
  is a different cause

Both warrant their own investigation slices (PR-157 and PR-158
respectively, when needed).

## Files Changed

| File | Lines |
|------|-------|
| `ecm-frontend/e2e/route-fallback-no-blank.mock.spec.ts` | +6 / -0 |
| `ecm-frontend/e2e/startup-visibility-sla.mock.spec.ts` | +7 / -0 |

No production code change. No new helper. Same usage shape as
PR-151 / PR-152.

## Sequencing

| PR | Role | Status |
|----|------|--------|
| PR-148 | Phase 5 Mocked: noise-filter (bypass) | ✅ shipped + CI-validated passing |
| PR-150 | Phase 5 Mocked: chunk-load (bypass) | ✅ shipped — pending verdict |
| PR-151 | Phase 5 Mocked: bootstrap-startup-fallback (mixed) + helper | ✅ shipped — :114 passes |
| PR-152 | Phase 5 Mocked: app-error-boundary-recovery (mock) | ✅ shipped — pending |
| PR-155 | Helper: fulfill → abort | ✅ shipped — pending |
| **PR-156** | **Phase 5 Mocked: route-fallback + startup-sla (mock)** | **✅ shipped this turn** |
| PR-157 | search-suggestions investigation | Pending |
| PR-158 | admin-audit-filter-export:6 investigation | Pending |

## Memory entries unchanged

`feedback_phase5_mocked_keycloak_strategy.md` and
`feedback_diagnostic_cadence_for_opaque_500s.md` already document
the patterns this commit follows. No new memory entry needed; the
implementation-note amendment about `route.abort` (mentioned in
PR-155's MD) can wait until PR-155 + PR-156's CI both confirm green.

## Why ship this without waiting for PR-155's CI verdict

Two reasons:

1. **Independence of slices**: PR-156's tests use the same helper
   PR-155 just fixed. If PR-155's abort approach is wrong, PR-156's
   tests will fail in the same way as before — no incremental
   damage. If PR-155 is right (the diagnosis from the artifact
   evidence is solid), PR-156 lets ~6 tests go green in one CI
   round instead of two.

2. **Pattern is already validated**: PR-148's noise-filter run
   confirmed the bypass approach works (732ms / 693ms passing).
   PR-155's abort fix targets a specific artifact-named symptom
   (the JSON-as-page render) that's diagnosable from the
   `error-context.md` snapshot — no speculation involved.

If `beca1cf` CI shows the abort approach was wrong, both PR-155
and PR-156 revert together via a single `git revert`.

## Non-goals

- Did not touch `search-suggestions-save-search:4, :184` — failure
  pattern is ~1s, not the 1.1m Keycloak hang. Different root cause.
- Did not touch `admin-audit-filter-export:6` — already uses bypass;
  its 32.5s timeout is a different cause that needs artifact-based
  investigation
- Did not change the helper itself — same as PR-155
- Did not modify production code

## Recommended next-turn behaviour

If `6200639` CI shows:

- **Phase 5 Mocked failure shrinks to ~2 specs**: success — open
  PR-157 (search-suggestions) and/or PR-158 (audit-export) as
  separate investigations
- **Phase 5 Mocked failure stays at ~6 specs (PR-155 abort wrong)**:
  revert PR-155 and PR-156; rethink the Keycloak-mock approach —
  possibly a `page.routeFromHAR(...)` or similar
- **Phase 5 Mocked goes green**: notification lane closeout flips
  to `accepted`; email lane (PR-159+) starts
