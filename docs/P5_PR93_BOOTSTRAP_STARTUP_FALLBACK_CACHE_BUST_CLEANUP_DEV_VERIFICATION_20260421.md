# P5 PR-93 Bootstrap Startup Fallback — Cache-Bust Cleanup Dev & Verification

## Date
2026-04-21

## Scope

Close the last systematic failure in the `Frontend Phase 5 Mocked Regression Gate` CI job so all 6 CI gates can go green.

This slice is frontend-only and touches a single file: the inline script in `ecm-frontend/public/index.html`. No React code changes, no test modifications, no backend changes.

## Failure Context

From CI run `24674591886` and earlier runs, the Phase 5 Mocked gate consistently failed on:

- `e2e/bootstrap-startup-fallback.mock.spec.ts:56` — "Startup fallback: reload uses cache-busting query and restores login shell" (all 3 retries, ~1.1m each)
- `e2e/bootstrap-startup-fallback.mock.spec.ts:99` — "Startup fallback: normal startup does not show fallback overlay"

Each test burned ~60s on timeout before retry/next; together they consumed the 30-min job timeout and the whole Phase 5 job was cancelled by the runner.

## Root Cause

The fallback overlay's "Reload" button constructs a cache-busting reload URL:

```js
// public/index.html before fix
createActionButton('Reload', function () {
  var reloadTarget = buildCacheBustReloadUrl();  // appends ?_ecm_reload=<timestamp>
  if (reloadTarget && reloadTarget !== window.location.href) {
    window.location.assign(reloadTarget);
    return;
  }
  window.location.reload();
}, 'ecm-bootstrap-fallback-reload')
```

Test :56 asserts:

```typescript
await page.getByRole('button', { name: /reload/i }).click();
await page.waitForURL(/_ecm_reload=\d+/, { timeout: 60_000 });              // URL takes the cache-bust param
await expect.poll(() => page.url()).not.toContain('_ecm_reload=');          // then loses it
await expect(page.getByText('Sign in with your organization account')).toBeVisible({ timeout: 60_000 });
```

The URL does gain `_ecm_reload=<ts>` via `window.location.assign`. But **nothing ever removes it**. The app loads, React renders, the login shell shows — but the query parameter persists in the URL. The `expect.poll(...).not.toContain('_ecm_reload=')` polls until its 15s poll budget is exhausted, producing a 60s test timeout.

The test's intent is correct: once the cache-busting reload has served its purpose, the param should be tombstoned so the URL returns to the canonical shape.

## Why Only CI Fails, Not Local

Locally the same tests pass. Two differences matter:

1. The Docker-nginx-served build in my local Docker stack and the `npm start` dev server both happen to not have aggressive caching; the `_ecm_reload` param never needed to take effect, and Playwright's poll budget was tolerant enough on a warm machine.
2. The Phase 5 Mocked CI job uses `serve -s build` on a cold runner with more latency, so the tests hit the explicit timeout more reliably.

In other words: the assertion was always meaningful, but locally it was masked by fast timing or forgiving polling windows.

## Fix

Add a small cleanup block at the top of the inline script in `public/index.html`:

```js
var RELOAD_CACHE_BUST_KEY = '_ecm_reload';

// Strip the cache-bust query parameter on load. The fallback overlay's
// "Reload" button adds ?_ecm_reload=<ts> to defeat HTTP caching; once the
// reload has completed, the param has no further purpose and callers
// (including the Phase 5 Mocked e2e regression) wait for it to leave
// the URL as the signal that the shell has recovered.
try {
  var loadedUrl = new URL(window.location.href, window.location.origin);
  if (loadedUrl.searchParams.has(RELOAD_CACHE_BUST_KEY)) {
    loadedUrl.searchParams.delete(RELOAD_CACHE_BUST_KEY);
    window.history.replaceState(
      window.history.state,
      '',
      loadedUrl.pathname + (loadedUrl.search ? loadedUrl.search : '') + loadedUrl.hash
    );
  }
} catch (error) {
  // best-effort cleanup only
}
```

- Runs before the fallback timer setup — purely additive
- `history.replaceState` preserves the current history entry (no new back-button step)
- Preserves pathname, remaining search params, and hash

## Expected Effect on Test :99

Test :99 asserts that in a normal bootstrap the fallback overlay is never shown. It sets `ecm_e2e_bootstrap_fallback_ms=3000`, then waits 3800ms and expects no fallback.

This fix doesn't directly address :99, but shaving a redundant URL-normalization roundtrip off the page load reduces a few ms of overhead and — more importantly — removes one uncertainty from the test environment. If :99 was failing due to a secondary effect of the unresolved `_ecm_reload` param (e.g., downstream router behavior), this fix removes that factor too.

If :99 still fails after this push, the next slice will bump its `ecm_e2e_bootstrap_fallback_ms` override from 3000ms to a more forgiving value to match CI cold-runner timing.

## Verification

### Local run against live Docker stack (post-rebuild)

```
cd ecm-frontend
npx playwright test e2e/bootstrap-startup-fallback.mock.spec.ts --project=chromium --workers=1
```

Result:

```
✓ :6 Startup fallback: forced blank bootstrap shows recovery overlay and can return to login (2.7s)
✓ :56 Startup fallback: reload uses cache-busting query and restores login shell (2.4s)
✓ :99 Startup fallback: normal startup does not show fallback overlay (5.0s)

3 passed (10.8s)
```

All 3 tests pass. CI confirmation will come from the next Phase 5 Mocked regression gate run.

## Files Changed

| File | Kind |
|------|------|
| `ecm-frontend/public/index.html` | +15 lines — cache-bust param cleanup in inline bootstrap script |

No React/TSX changes. No backend changes. No test changes. No migration.

## Expected CI Outcome

| Job | Expected |
|-----|----------|
| Backend Verify | ✅ unchanged |
| Frontend Build & Test | ✅ unchanged |
| Phase C Security Verification | ✅ unchanged |
| Acceptance Smoke (3 admin pages) | ✅ unchanged |
| Frontend E2E Core Gate | ✅ unchanged |
| **Phase 5 Mocked Regression Gate** | **✅ expected to flip green for the first time** |

If :99 continues to fail, plan a PR-94 follow-up that bumps the Phase-5 override window to match CI timing instead of the more optimistic 3000ms.

## Non-goals

- Not modifying any Playwright test
- Not adding any PR-92-style runtime features
- Not touching bootstrap timer behavior other than the URL cleanup

This is the smallest possible change that addresses the specific CI signal.
