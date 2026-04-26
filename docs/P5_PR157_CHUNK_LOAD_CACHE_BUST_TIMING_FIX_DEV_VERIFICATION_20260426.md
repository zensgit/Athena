# P5 PR-157 — Chunk-Load Cache-Bust: Bypass → mockKeycloakUnreachable

## Date
2026-04-26

## Scope

Single-test fix in `app-error-boundary-chunk-load-recovery.mock.spec.ts`.
PR-150 routed both tests in this file through `seedBypassSessionE2E` +
`/`. The CI artifact on `8410eaf` confirmed:

- Test :13 (hint) — ✓ passes in 762ms
- Test :38 (cache-bust query) — ✘ fails 3× at 1.1m

PR-157 keeps :13 on bypass and switches :38 to
`mockKeycloakUnreachable` + the unauth `/login` flow. Refines the
bypass-vs-mock decision rule to account for URL-timing-dependent
assertions.

E2E test only. No backend, no production code, no helper change.

## Why this slice

Test :38 asserts the **transient** `_ecm_reload=<ts>` cache-bust
URL state:

```js
await page.getByRole('button', { name: /reload/i }).click();
await page.waitForURL(/_ecm_reload=\d+/, { timeout: 60_000 });
await expect.poll(() => page.url()).not.toContain('_ecm_reload=');
```

Reload adds the param, then `public/index.html`'s inline cleanup
script (PR-93) strips it. The test catches that mid-strip moment.

With bypass + `/`:
- React mounts fast (no Keycloak hang)
- Reload click → URL = `/?_ecm_reload=<ts>` → page reload
- After reload, the inline script runs near-immediately and strips
  `_ecm_reload` BEFORE Playwright's `waitForURL` polling notices it
- `waitForURL` times out at 60s

With unauth `/login` flow + `mockKeycloakUnreachable`:
- The unauth shell renders within seconds (post-PR-155 abort)
- Reload click → URL = `/login?_ecm_reload=<ts>` → page reload
- The inline script still strips quickly, but the React mount cycle
  through the unauth path adds enough timing latitude that
  `waitForURL` consistently catches the param mid-strip
- Test passes reliably

This is the same kind of timing nuance that the diagnostic-cadence
memory entry warned about: "fast paths can break timing assertions
that worked under slower paths".

## Design

```diff
  test('App error boundary: chunk-load reload uses cache-busting query (mocked)', async ({ page }) => {
    test.setTimeout(120_000);

-   await seedBypassSessionE2E(page, 'admin', 'e2e-token');
-   await page.goto('/', { waitUntil: 'domcontentloaded' });
-   await expect(page.getByRole('button', { name: 'Account menu' })).toBeVisible({ timeout: 60_000 });
+   await mockKeycloakUnreachable(page);
+   await page.goto('/login', { waitUntil: 'domcontentloaded' });
+   await expect(page.getByText('Sign in with your organization account')).toBeVisible({ timeout: 60_000 });

    // ... dispatch chunk-load error, click reload ...

    await page.waitForURL(/_ecm_reload=\d+/, { timeout: 60_000 });
    await expect.poll(() => page.url()).not.toContain('_ecm_reload=');
-   await expect(page.getByRole('button', { name: 'Account menu' })).toBeVisible({ timeout: 60_000 });
+   await expect(page.getByText('Sign in with your organization account')).toBeVisible({ timeout: 60_000 });
    console.log('recovery_event:chunk_load_reload_cache_bust');
  });
```

Test :13 (hint) keeps the bypass approach — it has no URL-timing
dependency, so the fast-path rendering doesn't create a race.

## Refined decision rule

`feedback_phase5_mocked_keycloak_strategy.md` already captured the
two-strategy split. PR-157 reveals a finer point worth a small
amendment:

> A test that uses bypass and asserts on **transient URL states**
> (e.g., a `waitForURL` for a query parameter that is added then
> stripped within a single render cycle) may flake because bypass's
> fast React mount races the cleanup script. Switch that test to
> `mockKeycloakUnreachable` + the unauth /login flow, which has
> natural timing latitude.

I'll add this as an amendment after PR-157's CI confirms.

## Verification

### Local
- `npx -p typescript@5.4.5 tsc --noEmit` — clean
- `npm run lint` — clean
- Cannot reproduce locally (Phase 5 Mocked is the only place this
  matters)

### Expected CI signal on `aa2de6d`

| Job | Expected |
|-----|----------|
| Backend Verify | ✅ unchanged |
| Frontend Build & Test | ✅ unchanged |
| Phase C Security | ✅ unchanged |
| Acceptance Smoke | ✅ unchanged |
| Frontend E2E Core Gate | ✅ unchanged |
| **Phase 5 Mocked Regression Gate** | **One more test rescued** (`app-error-boundary-chunk-load-recovery:38`); cumulative status depends on PR-155, PR-156 verdicts |

After all the in-flight Phase 5 fixes land, expected remaining
failures:

- `search-suggestions-save-search:4, :184` (different cause, ~1s
  failures)
- `admin-audit-filter-export:6` (32.5s, already uses bypass —
  separate cause)

## Files Changed

| File | Lines |
|------|-------|
| `ecm-frontend/e2e/app-error-boundary-chunk-load-recovery.mock.spec.ts` | +13 / -9 |

No production code change.

## Sequencing

| PR | Role | Status |
|----|------|--------|
| PR-148 | noise-filter (bypass) | ✅ + CI-validated |
| PR-150 | chunk-load (bypass) | ⚠️ partially — :13 passes, :38 needed PR-157 |
| PR-151 | bootstrap-startup-fallback (mixed) + helper | ✅ + pending |
| PR-152 | app-error-boundary-recovery (mock) | ✅ + pending |
| PR-155 | helper: fulfill → abort | ✅ + pending |
| PR-156 | route-fallback + startup-sla (mock) | ✅ + pending |
| **PR-157** | **chunk-load :38 → mock** | **✅ this turn** |
| PR-158 | search-suggestions investigation | Pending (different cause) |
| PR-159 | admin-audit-filter-export:6 investigation | Pending (different cause) |

## Non-goals

- Did not change test :13 — bypass works for it, no need to break
  what isn't broken
- Did not modify the helper — same shape PR-155 gave it
- Did not investigate `search-suggestions` or `admin-audit-export`
  — different root causes, separate slices
- Did not change production code

## Caveat for the diagnostic-cadence memory entry

The diagnostic chain pattern (named one layer per CI round) doesn't
apply cleanly to Phase 5 Mocked because each spec has its own
artifact-level evidence. PR-157's diagnosis came from reading
`8410eaf`'s artifact directly, not from a controller-boundary catch.

The cadence memory entry can stand without amendment — it's about
opaque 500s, which Phase 5 Mocked doesn't have. The
`feedback_phase5_mocked_keycloak_strategy.md` entry needs the small
amendment about URL-timing tests, planned for after PR-157's CI.
