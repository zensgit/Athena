# P5 PR-155 — `mockKeycloakUnreachable` Abort Fix

## Date
2026-04-26

## Scope

Fix to the `mockKeycloakUnreachable` helper introduced in PR-151.
The current `route.fulfill(...)` implementation intercepts top-level
navigation triggered by keycloak-js's redirect-to-auth flow and
renders the JSON body as the page content. Replace with
`route.abort('connectionfailed')` so the helper precisely simulates
"Keycloak truly unreachable".

E2E helper only. No backend, no production code, no test
spec change in this slice (the affected specs continue to use the
same helper, now with corrected semantics).

## Why this slice

PR-148/150/151/152 + PR-149/153/154 closed the **notification lane**:
the `Run RM notification acceptance gate` step on `8410eaf` was
"1 flaky, 3 passed" → step success. PR-154's `{ exact: true }`
locator fix in flight will make it deterministic.

But the **Phase 5 Mocked rollout** is partially working:

| Run | Phase 5 Mocked verdict |
|-----|------------------------|
| `11809e3` (predates rollout) | Cancelled at 30 min — expected |
| `9b81041` (predates PR-151) | Cancelled at 30 min — expected |
| **`8410eaf` (PR-148+149+150+151)** | **Failure within budget** — first time the suite ever finished |
| `3708ba8` (adds PR-152) | Still in progress |

Phase 5 Mocked finishing within budget for the first time is
itself proof that PR-148 + PR-150 + PR-151's `:114` + PR-152
collectively returned enough time to the budget. But four tests
still fail under `mockKeycloakUnreachable`:

- `bootstrap-startup-fallback:19` (was `:6`, forced-blank → fallback)
- `bootstrap-startup-fallback:70` (was `:56`, reload cache-bust)
- `route-fallback-no-blank:77` (unauth route fallback)
- `startup-visibility-sla:88` (login route SLA)

(Test `:114` "normal startup" uses `seedBypassSessionE2E`, not the
mock, and it passes.)

The artifact `error-context.md` for the forced-blank failure shows
the page snapshot is:

```yaml
{"error":"unauthorized","error_description":"no session"}
```

That's a literal JSON body rendered as the page. keycloak-js's
"no session, redirect to auth" flow does
`window.location.href = "{KEYCLOAK_URL}/realms/{realm}/protocol/openid-connect/auth?..."`.
With `route.fulfill({status: 401, body: JSON})`, the browser:
1. Receives a 401 response with `application/json` body
2. Renders the JSON as text on a blank page
3. Bootstrap-startup-fallback overlay never appears (the React app
   isn't running on the JSON page)

The mock was too aggressive. It intercepted not just AJAX calls
(which keycloak-js init's `.well-known/openid-configuration` request
expects to fail) but also top-level navigation, which the test does
not want to navigate.

## Design

```diff
- await route.fulfill({
-   status: 401,
-   contentType: 'application/json',
-   body: JSON.stringify({ error: 'unauthorized', error_description: 'no session' }),
- });
+ await route.abort('connectionfailed');
```

`route.abort('connectionfailed')` simulates a connection-refused
result for **all** request types under `**/realms/**`:

- AJAX/fetch from keycloak-js's init `.well-known/openid-configuration`
  rejects fast → keycloak-js gives up → app falls into unauth state
- Top-level redirect via `window.location.href = realms-auth-url`
  fails → browser stays on the original page → no "JSON-as-page"
  glitch
- Iframe SSO check requests fail → same fast-fail path

This matches what "Keycloak truly unreachable" looks like to the
browser. The bootstrap-startup-fallback timer in `public/index.html`
runs as designed (no React mount → root has no children at
fallback_ms → overlay appears).

## Why this matters for the systemic plan

The `feedback_phase5_mocked_keycloak_strategy.md` memory entry
documents the bypass-vs-Keycloak-mock decision rule. PR-155
strengthens the second option (the Keycloak mock) so it's actually
correct for the case where the test needs the unauth shell to
render. Without this fix, `mockKeycloakUnreachable` would be
unsafe to use anywhere — limiting Phase 5 Mocked coverage.

The decision rule remains:
- **Page-agnostic test** → `seedBypassSessionE2E`
- **Unauth /login flow IS subject** → `mockKeycloakUnreachable` (now correct)

## Verification

### Local
- `npx -p typescript@5.4.5 tsc --noEmit` — clean
- `npm run lint` — clean
- Cannot reproduce locally (whole point of Phase 5 Mocked is
  static-serve env)

### Expected CI signal on `beca1cf`

| Job | Expected |
|-----|----------|
| Backend Verify | ✅ unchanged (no backend) |
| Frontend Build & Test | ✅ unchanged (no Jest tests touched) |
| Phase C Security | ✅ unchanged |
| Acceptance Smoke | ✅ unchanged |
| **Frontend E2E Core Gate** | **✅** — PR-154's locator fix combined with this mock fix should land it deterministically |
| **Phase 5 Mocked Regression Gate** | **Two more tests rescued** (`bootstrap-startup-fallback:19` and `:70`) — possibly all four Keycloak-mock-using tests in this commit (also `route-fallback-no-blank:77` if it uses the same helper, and `app-error-boundary-recovery:5` from PR-152) |

The remaining Phase 5 Mocked failures after PR-155 (most likely):

- `route-fallback-no-blank:77` — does NOT currently use the helper; needs PR-156 to add it
- `startup-visibility-sla:88` — same
- `search-suggestions-save-search:4` and `:184` — different cause (failures at <1s, not the 1.1m Keycloak hang)
- `admin-audit-filter-export:6` — already uses bypass, separate cause

So Phase 5 Mocked may still fail on `beca1cf`, but with even fewer
red tests. PR-156+ continues the rollout.

## Files Changed

| File | Lines |
|------|-------|
| `ecm-frontend/e2e/helpers/keycloakMock.ts` | +13 / -11 |

No production code change. No new test, no migration.

## Sequencing

| PR | Role | Status |
|----|------|--------|
| PR-148 | Phase 5 Mocked PoC (noise-filter, bypass) | ✅ shipped + CI-validated (passing) |
| PR-149 | Per-preset REQUIRES_NEW isolation (notification lane) | ✅ shipped + CI-validated |
| PR-150 | Phase 5 Mocked rollout (chunk-load, bypass) | ✅ shipped + Phase 5 within budget |
| PR-151 | Phase 5 Mocked rollout (bootstrap, mixed strategy) + helper | ✅ shipped — `:114` passes; `:19/:70` need PR-155's mock fix |
| PR-152 | Phase 5 Mocked rollout (recovery, Keycloak mock) | ✅ shipped — pending CI verdict |
| PR-153 | Inner-loop INFO logs (notification diagnostic) | ✅ shipped (residual) |
| PR-154 | Notification gate locator strict-match | ✅ shipped + CI in flight |
| **PR-155** | **mockKeycloakUnreachable: fulfill → abort** | **✅ shipped this turn** |
| PR-156 | Add helper to remaining mockable specs (route-fallback-no-blank, startup-visibility-sla) | Pending |

## Memory entry implications

`feedback_phase5_mocked_keycloak_strategy.md` should be amended
once this fix lands green:

> **Implementation note**: `mockKeycloakUnreachable` aborts every
> `**/realms/**` request rather than fulfilling — fulfilling
> intercepts keycloak-js's `window.location.href` redirect and
> renders the JSON body as the page (artifact evidence: `8410eaf`'s
> `bootstrap-startup-fallback` failures had `error-context.md` page
> snapshots = `{"error":"unauthorized",...}`).

I'll add this on the next memory-update turn.

## Non-goals

- Did not extend the helper to also mock localhost:8180 specifically
  — `**/realms/**` matches both relative and absolute Keycloak URLs
  via Playwright's glob behaviour
- Did not add a complementary helper that returns a fake Keycloak
  configuration for tests that NEED a working Keycloak (e.g., real
  login flow) — that's a separate capability if/when needed
- Did not change any test spec — same helper, same usage sites,
  better semantics
- Did not investigate the `search-suggestions-save-search` or
  `admin-audit-filter-export:6` failures — different root causes,
  separate slices

## What success looks like after `beca1cf` CI

- `bootstrap-startup-fallback:19` and `:70` — green
- (Maybe) `app-error-boundary-recovery` from PR-152 — green
- Phase 5 Mocked may still cancel/fail on the remaining
  unfixed specs but the count of failing tests drops further

The diagnostic-cadence memory entry's pattern continues: each round
narrows one named layer. Wall-clock cost ~30 min per round, but
deterministic.
