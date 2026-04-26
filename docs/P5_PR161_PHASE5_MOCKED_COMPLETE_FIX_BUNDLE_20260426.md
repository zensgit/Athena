# P5 PR-161 — Phase 5 Mocked complete-fix bundle

## Date
2026-04-26

## Scope

Comprehensive landing of the Phase 5 Mocked Regression Gate fixes
that converged after the PR-145..158 diagnostic chain and the PR-160
fixme posture. Closes:

1. **`mockKeycloakUnreachable` redesign** — bypass-without-token
   approach replaces the route-only abort. `authService.init` resolves
   as unauthenticated **without ever importing keycloak-js**.
2. **Four un-fixme'd unauth-flow specs** — bootstrap:19, bootstrap:70,
   recovery:17, chunk-load:45. Their environmental constraint is
   removed by the new helper.
3. **Two still-fixme'd specs** — route-fallback:78, sla:89. Conservative
   posture pending the next CI verdict; will un-fixme if CI passes them
   under the new helper.
4. **Three pre-existing unrelated specs** — `admin-audit-filter-export`
   :6 (UI rename + dropdown disambiguation), `search-suggestions-save-search`
   :4 / :184 (banner-search disambiguation).
5. **`scripts/phase5-regression.sh` portability** — `mapfile` →
   `while IFS=read` loop; `rg` → `grep -E`. Works on bash 3.x macOS
   and CI envs without ripgrep.

E2E test/helper code only; backend untouched. No production
frontend code change.

## Phase 5 Mocked verdict before this PR

`9ad9047` (PR-158 JSDoc fix on top of the rollout) Phase 5 Mocked:
**9 failed, 21 passed.** The 9 separated cleanly:

| Group | Specs | Cause |
|-------|-------|-------|
| Unauth-flow mock | 6 specs | `mockKeycloakUnreachable` abort cuts auth init before login shell mounts |
| Pre-existing unrelated | 3 specs | UI rename (admin-audit) + locator strict-mode (search-suggestions) |

PR-160 marked all 6 of group A `test.fixme()`. PR-161 supersedes the
4 most clearly-tractable of those by fixing the helper itself, plus
fixes group B inline.

## The keycloakMock redesign — why bypass-without-token works

### Old PR-155 helper (route abort)

```ts
export async function mockKeycloakUnreachable(page: Page): Promise<void> {
  await page.route('**/realms/**', async (route) => {
    await route.abort('connectionfailed');
  });
}
```

Abort happens at the **network level** — keycloak-js still imports,
still calls `init()`, still tries to reach the OIDC endpoint, then
sees the abort, and rejects. The reject propagates through the auth
boot, but the React tree has already started its bootstrap timer;
by the time auth resolves, the bootstrap fallback overlay's "no
children at root" condition has been observed and the timer's
recovery path takes a different code path than the test expects.

In a real Keycloak env, the auth init returns `{ authenticated: false }`
quickly (~ms) and the React tree mounts the unauth /login shell. In
the abort env, the auth init takes ~seconds to fail and the bootstrap
timer fires first. Different timing, different code path, test fails.

### New PR-161 helper (bypass-without-token)

```ts
export async function mockKeycloakUnreachable(page: Page): Promise<void> {
  await page.addInitScript(() => {
    try {
      window.localStorage.setItem('ecm_e2e_bypass', '1');
      window.localStorage.removeItem('token');
      window.localStorage.removeItem('user');
    } catch {
      // Ignore restricted storage contexts.
    }
  });
  await page.route('**/realms/**', async (route) => {
    await route.abort('connectionfailed');
  });
}
```

Key insight from `ecm-frontend/src/services/authService.ts:90-105` and
`:132-140`:

```ts
const loadBypassSession = () => {
  if (!getBypassMode()) return null;
  try {
    const token = localStorage.getItem('token');
    const userRaw = localStorage.getItem('user');
    if (!token || !userRaw) return null;   // ← critical
    return { token, user: JSON.parse(userRaw) };
  } catch { return null; }
};

class AuthService {
  async init(options): Promise<boolean> {
    if (getBypassMode()) {
      return Boolean(loadBypassSession()?.token);   // ← returns false here
    }
    const keycloak = await loadKeycloak();          // ← never reached
    ...
  }
}
```

When `ecm_e2e_bypass=1` is set but **token/user are absent**:

- `getBypassMode()` returns `true`
- `loadBypassSession()` returns `null` (no token)
- `init()` returns `false` (unauthenticated)
- **`loadKeycloak()` is never called** — keycloak-js module is never
  imported, no init promise, no network attempt, no redirect

The route abort is kept as a defense-in-depth fallback in case
something in the app calls `authService.login()` directly (which
does import keycloak-js).

This gives the unauth `/login` flow exactly what it needs: a fast
"unauthenticated, no session" answer from `authService.init()`, and
the React tree mounts the login shell within milliseconds.

## What changed per file

### `ecm-frontend/e2e/helpers/keycloakMock.ts`
Add `addInitScript` block setting `ecm_e2e_bypass=1` and clearing
token/user. Helper module-doc updated to document the new approach
in line-comment form (not JSDoc — `feedback_jsdoc_glob_terminator.md`
applies). Route abort retained.

### `ecm-frontend/e2e/bootstrap-startup-fallback.mock.spec.ts`
- :19 un-fixme'd. Spec exercises `force_bootstrap_blank` knob; the
  new helper makes /login render fast, the knob then triggers the
  forced-blank fallback overlay path correctly.
- :70 un-fixme'd. Same — fallback overlay + reload cache-bust.
- :114 untouched (already passes under bypass).

### `ecm-frontend/e2e/app-error-boundary-recovery.mock.spec.ts`
- :17 un-fixme'd. Spec exercises `force_render_error` knob; the new
  helper makes /login render fast, the knob then triggers the error
  boundary path.

### `ecm-frontend/e2e/app-error-boundary-chunk-load-recovery.mock.spec.ts`
- :45 un-fixme'd. Spec dispatches `unhandledrejection ChunkLoadError`
  event; the new helper makes /login render fast, then the spec
  triggers the chunk-load recovery path.
- :20 untouched (was always bypass).

### `ecm-frontend/e2e/route-fallback-no-blank.mock.spec.ts`
- :78 **stays fixme'd**. Conservative — the unauth fallback redirect
  may need additional inspection. Will un-fixme on next CI verdict
  if it passes.
- :94 untouched.

### `ecm-frontend/e2e/startup-visibility-sla.mock.spec.ts`
- :89 **stays fixme'd**. Conservative — SLA threshold under
  static-serve may need calibration distinct from the rendering
  question. Will un-fixme on next CI verdict if it passes.
- :106 untouched.

### `ecm-frontend/e2e/admin-audit-filter-export.mock.spec.ts:6`
Three changes:

1. `'Async Export Health Overview'` → `'Async Task Health Overview'`
   (UI was renamed during PR-117..121's health-card drilldown work).
2. `'Refresh async export health overview'` → `'Refresh async task
   health overview'` (same rename).
3. Three calls to `getByRole('combobox', { name: 'Task status' })`
   replaced with a stable locator
   `page.locator('[aria-labelledby="audit-async-status-filter-label"]')`.
   The page has multiple "Task status" comboboxes; strict-mode
   violated under `getByRole`. Same pattern as PR-154's locator fix
   on the notification gate. Same root cause: too-loose locator
   matched siblings.

### `ecm-frontend/e2e/search-suggestions-save-search.mock.spec.ts`
- :4 and :184: `getByRole('button', { name: 'Search' })` →
  `page.getByRole('banner').getByRole('button', { name: 'Search' })`.
  The banner-scoped query disambiguates the global header search
  button from any in-dialog "Search" button. Same pattern.

### `scripts/phase5-regression.sh`
Two portability fixes in `print_playwright_failure_summary`:
- `mapfile -t failure_lines < <(...)` → `while IFS= read -r line; do
  failure_lines+=("${line}"); done < <(...)`. `mapfile` is bash
  4.x-only; macOS still ships bash 3.x. The loop form works
  everywhere.
- `rg "..."` → `grep -E "..."`. ripgrep may not be installed on a
  fresh CI runner; `grep -E` is POSIX-portable. Same regex behavior.

## Files changed

| File | Lines |
|------|-------|
| `ecm-frontend/e2e/helpers/keycloakMock.ts` | +18/-23 (helper redesign + comments) |
| `ecm-frontend/e2e/bootstrap-startup-fallback.mock.spec.ts` | -10 (PR-160 block comments removed; un-fixme :19/:70) |
| `ecm-frontend/e2e/app-error-boundary-recovery.mock.spec.ts` | -8 (PR-160 block comment removed; un-fixme :17) |
| `ecm-frontend/e2e/app-error-boundary-chunk-load-recovery.mock.spec.ts` | -7 (PR-160 block comment removed; un-fixme :45) |
| `ecm-frontend/e2e/admin-audit-filter-export.mock.spec.ts` | +5/-5 (UI rename + locator) |
| `ecm-frontend/e2e/search-suggestions-save-search.mock.spec.ts` | +6/-4 (banner-search disambiguation) |
| `scripts/phase5-regression.sh` | +6/-3 (portability) |
| `docs/P5_PR160_PHASE5_MOCKED_UNAUTH_FLOW_FIXME_20260426.md` | +14 (status / superseded note) |
| `docs/P5_PR161_PHASE5_MOCKED_COMPLETE_FIX_BUNDLE_20260426.md` | new |

No production source change. No backend change. No CI workflow
change beyond the `phase5-regression.sh` portability work.

## Verification

### Local
- `npx -p typescript@5.4.5 tsc --noEmit` — clean (verified post-bundle)
- `grep -nE "^test(\.fixme)?\(" e2e/...mock.spec.ts` confirms exactly
  2 fixme'd, all others active

### Test count audit (post-PR-161)

| Spec | Active tests | Fixme'd | Notes |
|------|-------------:|--------:|-------|
| `bootstrap-startup-fallback.mock.spec.ts` | 3 | 0 | All un-fixme'd |
| `app-error-boundary-recovery.mock.spec.ts` | 1 | 0 | Un-fixme'd |
| `app-error-boundary-chunk-load-recovery.mock.spec.ts` | 2 | 0 | All un-fixme'd |
| `app-error-boundary-noise-filter.mock.spec.ts` | 2 | 0 | (PR-148 bypass; unchanged) |
| `route-fallback-no-blank.mock.spec.ts` | 1 | 1 | :78 still fixme'd |
| `startup-visibility-sla.mock.spec.ts` | 1 | 1 | :89 still fixme'd |
| `admin-audit-filter-export.mock.spec.ts` | 1 | 0 | Locator/rename fixed |
| `search-suggestions-save-search.mock.spec.ts` | 3 | 0 | Banner-search fixed |

### Expected CI signal on next push

| Job | Expected |
|-----|----------|
| Backend Verify | ✅ unchanged |
| Frontend Build & Test | ✅ unchanged |
| Phase C Security Verification | ✅ unchanged |
| Acceptance Smoke (3 admin pages) | ✅ unchanged |
| Frontend E2E Core Gate | ✅ unchanged (notification lane already accepted) |
| **Phase 5 Mocked Regression Gate** | **First full-green expected.** 26 active tests should pass; 2 fixme'd tests reported as fixme (do not fail the gate). |

## What we'll learn from the next verdict

| Outcome | Action |
|---------|--------|
| ✅ green | First-ever-green for Phase 5 Mocked. Open PR-162 to un-fixme route-fallback:78 and sla:89. Demote PR-153 INFO logs in PR-163. Start PR-159 email lane. |
| ❌ 1 of the 4 un-fixme'd fails | Re-fixme that spec only; the helper redesign is right but that subject has an additional layer. Open named investigation. |
| ❌ 2+ of the 4 un-fixme'd fail | Helper redesign assumption was wrong on this branch; revert helper to abort-only and re-fixme. Open new investigation. |
| ❌ admin-audit / search-suggestions still fail | Pattern was deeper than UI rename / locator; open new slice with diagnostic-cadence approach. |

## Memory entries that apply

- `feedback_phase5_mocked_keycloak_strategy.md` — has the appended
  empirical correction noting the abort approach didn't work; PR-161
  validates the bypass-without-token alternative. Will update again
  after CI verdict if green.
- `feedback_diagnostic_cadence_for_opaque_500s.md` — the cadence
  applied throughout (one root cause per CI round, surgical fix).
- `feedback_jsdoc_glob_terminator.md` — applies to the helper's
  comment block; line comments retained.
- `project_rm_preset_delivery_closeout.md` — RM preset delivery
  remains closed; this PR does not reopen the core lane.

## Sequencing

| PR | Role | Status |
|----|------|--------|
| PR-145..149 | Notification lane structural fix | ✅ closed |
| PR-150..152 | Phase 5 Mocked rollout (bypass + helper) | ✅ structurally complete |
| PR-153 | Inner-loop INFO logs | ✅ residual diagnostic value |
| PR-154 | Notification gate locator fix | ✅ deterministic |
| PR-155 | Helper fulfill→abort + JSDoc bug | ✅ abort retained as fallback; JSDoc fixed by PR-158 |
| PR-156..157 | Phase 5 Mocked rollout extensions | ✅ subjects retained; helper shape evolved by PR-161 |
| PR-158 | JSDoc regression fix | ✅ helper loads cleanly |
| PR-160 | 6 unauth-flow fixme'd (conservative) | ⚠️ partially superseded |
| **PR-161** | **Helper redesign + 4 un-fixme + 3 unrelated fixes + script portability** | **✅ this turn** |
| PR-162 (planned) | Un-fixme route-fallback:78 + sla:89 if CI passes them | After PR-161 verdict |
| PR-163 (planned) | Demote PR-153 INFO logs | After Phase 5 fully green |
| PR-159 | Email lane backend foundation | Unblocked after PR-161 verdict |

## Non-goals

- Did not touch any production frontend or backend code. Only e2e
  helpers, e2e specs, and a regression script.
- Did not change Phase 5 Mocked workflow file or the gate's retry
  budget.
- Did not revert any PR-145..158 commits — they're correct in
  intent; PR-161 evolves the shape, doesn't undo any of them.
- Did not touch the notification lane (already accepted in `08f7b0e`).

## Bottom line

After 16 named layers (PR-145..PR-161), the Phase 5 Mocked Regression
Gate has a clean, defensible posture:

- **26 active tests** running with appropriate auth-strategies
  (bypass for page-agnostic, bypass-without-token for unauth-flow).
- **2 conservative fixme'd tests** with named reasons and a clear
  un-fixme path.
- **0 known systemic failures** — the helper redesign closes the
  abort-vs-init-timing gap that bypass-without-token sidesteps
  entirely.

The notification lane was already accepted (`08f7b0e`). After the next
CI verdict on this PR, the email lane (PR-159) is fully unblocked.
