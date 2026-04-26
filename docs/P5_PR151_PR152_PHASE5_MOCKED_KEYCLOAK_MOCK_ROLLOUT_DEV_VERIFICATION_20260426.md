# P5 PR-151 + PR-152 — Phase 5 Mocked Keycloak Mock Rollout

## Date
2026-04-26

## Scope

Two coordinated commits that complete the Phase 5 Mocked rollout
plan from
`docs/P5_PHASE5_MOCKED_GATE_INVESTIGATION_DEV_VERIFICATION_20260426.md`:

1. **PR-151 (`8410eaf`)** — `bootstrap-startup-fallback.mock.spec.ts`
   (3 tests, mixed strategy: 2 Keycloak-mock + 1 bypass) plus the
   new `e2e/helpers/keycloakMock.ts` helper.
2. **PR-152 (`3708ba8`)** — `app-error-boundary-recovery.mock.spec.ts`
   (1 test, Keycloak-mock approach since semantic intent is the
   unauth /login → back-to-login flow).

Pure e2e test changes. No production code change. No backend.

## Why two strategies

Investigation MD identified the right fix shape depends on the
test's semantic intent:

| Subject under test | Right fix | Why |
|--------------------|-----------|-----|
| Page-agnostic global behaviour (e.g., noise filter, chunk-load handler) | `seedBypassSessionE2E` + authenticated route | Behaviour fires on any page; bypass mounts React fast and avoids Keycloak entirely |
| Unauth /login flow IS the subject (e.g., recovery overlay → "Back to Login", forced bootstrap fallback) | `mockKeycloakUnreachable` + keep `/login` | Need /login to actually render; bypass would change semantic. Mock returns 401 fast so keycloak-js gives up and the unauth shell renders. |

PR-148 (noise-filter) and PR-150 (chunk-load) used bypass.
PR-151 and PR-152 introduce and use the Keycloak mock.

## Helper: `e2e/helpers/keycloakMock.ts`

```ts
export async function mockKeycloakUnreachable(page: Page): Promise<void> {
  await page.route('**/realms/**', async (route) => {
    await route.fulfill({
      status: 401,
      contentType: 'application/json',
      body: JSON.stringify({ error: 'unauthorized', error_description: 'no session' }),
    });
  });
}
```

Handles every Keycloak endpoint (`.well-known/openid-configuration`,
auth endpoint, iframe endpoints) with an immediate 401. The
keycloak-js adapter's `init()` sees that on the first endpoint and
resolves into the no-session state. The app's auth service then
renders the unauth /login shell.

**Decision rule for future Phase 5 Mocked specs** (worth memory entry):

- If the test goes through `/login` only as a "page is loaded"
  sanity check before exercising a global behaviour: use bypass.
- If the test deliberately exercises behaviour visible on the
  unauth /login render: use `mockKeycloakUnreachable`.
- If the test forces some other E2E knob (e.g.,
  `ecm_e2e_force_bootstrap_blank`): combine the knob with whichever
  of the above keeps semantic intent.

## PR-151 detail

### Three tests, three approaches

**Test 1** — "Forced blank bootstrap shows recovery overlay and can
return to login":
- Forces `ecm_e2e_force_bootstrap_blank=1` + `ecm_e2e_bootstrap_fallback_ms=600`
- The fallback overlay is rendered by `public/index.html`'s inline
  script, independent of React/Keycloak — it appears at 600ms even
  if Keycloak is broken
- Failure mode: with no Keycloak mock, the goto /login hangs at
  `domcontentloaded` budget or React's auth-init resource fetch
  → fallback timer might fire but Playwright's first
  `expect.toBeVisible(...)` budget is exhausted by the overall
  Keycloak-driven page lifecycle latency
- Fix: add `mockKeycloakUnreachable(page)` before `goto`. /login
  is reached and React's auth-init resolves quickly into the
  unauth state. The forced-blank flag still kicks in; fallback
  overlay still appears. "Back to Login" still routes correctly.
- Existing `recovery_event:` markers preserved.

**Test 56** — "Reload uses cache-busting query and restores login
shell":
- Same forced-blank setup as test 1
- Click reload → `_ecm_reload=<ts>` appended → cleanup script (PR-93)
  strips it after reload → expect "Sign in with your organization
  account" visible
- Fix: same `mockKeycloakUnreachable` approach. After reload, the
  mock still applies (`page.route` persists across navigations in
  the same test), so /login renders fast again with the sign-in
  text.

**Test 99** — "Normal startup does not show fallback overlay":
- `force_bootstrap_blank=0`, `bootstrap_fallback_ms=3000`
- Subject: the fallback timer's "no overlay when root has children
  at 3000ms" branch
- This branch is page-agnostic — it fires the same on /login or /,
  it's just the React mount that determines whether root has
  children
- Fix: switch to `seedBypassSessionE2E` + `goto /` so React mounts
  fast (well before the 3000ms cap) and root definitely has
  children. Assertion changes from "Sign in..." to "Account menu"
  — same "page is loaded" sanity check, different page.

## PR-152 detail

`app-error-boundary-recovery.mock.spec.ts` — single test:

> "App error boundary: forced render crash can recover to login"

- Goto /login → expect "Sign in..."
- Set `ecm_e2e_force_render_error=1`
- Goto /browse/root → forced render error
- Expect error overlay + "Back to Login" buttons
- Click "Back to Login" → expect /login URL + "Recovered from
  unexpected app error" recovery card

The "Back to Login" return is the test's whole point. Bypass would
log the user in, so /login becomes a redirect or wrong page. Use
`mockKeycloakUnreachable` instead.

The body is otherwise unchanged. The recovery flow exercises the
same React state machine; only the auth-init Keycloak call gets
short-circuited.

## Phase 5 Mocked rollout cumulative status

| Slice | Spec(s) | Test count | Approach |
|-------|---------|------------|----------|
| PR-148 | `app-error-boundary-noise-filter.mock.spec.ts` | 2 | Bypass + `/` |
| PR-150 | `app-error-boundary-chunk-load-recovery.mock.spec.ts` | 2 | Bypass + `/` |
| PR-151 | `bootstrap-startup-fallback.mock.spec.ts` | 3 | 2× Keycloak mock + 1× bypass |
| PR-152 | `app-error-boundary-recovery.mock.spec.ts` | 1 | Keycloak mock |
| **Total** | **4 spec files** | **8 test cases** | — |

Each previously failed at ~1.1m × 3 retries × per-test = ≈26 min
returned to the 30-min job budget.

After PR-152, the **only known failing mock spec** is:
`admin-audit-filter-export.mock.spec.ts:6` (32.5s timeout, already
uses bypass — different root cause). Warrants its own investigation
slice.

## Verification

### Local
- `npx -p typescript@5.4.5 tsc --noEmit` — clean for both PRs
- `npm run lint` — clean for both PRs
- Cannot run e2e locally (the whole point of the Phase 5 Mocked
  job's existence)

### CI signal expected on `3708ba8`

| Job | Expected |
|-----|----------|
| Backend Verify | ✅ unchanged |
| Frontend Build & Test | ✅ unchanged |
| Phase C Security | ✅ unchanged |
| Acceptance Smoke | ✅ unchanged |
| **Frontend E2E Core Gate** | **Notification step depends on PR-149 isolation; if green then notification lane closes** |
| **Phase 5 Mocked Regression Gate** | **8 previously-failing tests now passing**; only `admin-audit-filter-export:6` may keep the gate red (separate cause) |

PR-148 was already CI-validated (job `73031958008` on commit
`80a1275` → both noise-filter tests now pass at 732ms / 693ms).
The same approach + helper applied here should produce the same
outcome.

## Files Changed

| File | PR | Lines |
|------|----|-------|
| `ecm-frontend/e2e/helpers/keycloakMock.ts` | PR-151 | New, +37 |
| `ecm-frontend/e2e/bootstrap-startup-fallback.mock.spec.ts` | PR-151 | +28 / -2 |
| `ecm-frontend/e2e/app-error-boundary-recovery.mock.spec.ts` | PR-152 | +13 |

No production code change. No backend. No migration.

## Sequencing

| PR | Role | Status |
|----|------|--------|
| PR-148 | Phase 5 Mocked PoC (noise-filter, bypass) | ✅ shipped + CI-validated |
| PR-150 | Phase 5 Mocked rollout (chunk-load, bypass) | ✅ shipped |
| **PR-151** | **bootstrap-startup-fallback (mixed strategy + Keycloak mock helper)** | **✅ shipped this turn** |
| **PR-152** | **app-error-boundary-recovery (Keycloak mock)** | **✅ shipped this turn** |

After PR-152's CI run lands, Phase 5 Mocked is **expected to go
green for the first time on this repo** (per memory entry
`feedback_local_is_not_ci_verification.md`'s acknowledgement that
the gate has never been green). If `admin-audit-filter-export:6`
keeps it red, that's a separate slice.

## Memory entry recommendation

When Phase 5 Mocked goes green, add:

```
- [Phase 5 Mocked decision rule] feedback_phase5_mocked_keycloak_strategy.md —
  Page-agnostic test → seedBypassSessionE2E + authenticated route.
  Test where /login flow is subject → mockKeycloakUnreachable.
```

And demote `feedback_es_facet_aggregation_race.md`'s gate-level
implication (the search-preview-status flake remains documented but
is on Core Gate, not Phase 5 Mocked, and not gate-blocking).

## Non-goals

- Did not touch `admin-audit-filter-export.mock.spec.ts:6` —
  already uses bypass; different root cause warrants its own
  investigation slice
- Did not change any production code
- Did not change `phase5-regression.sh` or any CI workflow
- Did not investigate why the bootstrap fallback timer's
  Playwright-side wait was timing out specifically (the helper
  fixes the upstream cause; investigating downstream symptom is
  unnecessary)
