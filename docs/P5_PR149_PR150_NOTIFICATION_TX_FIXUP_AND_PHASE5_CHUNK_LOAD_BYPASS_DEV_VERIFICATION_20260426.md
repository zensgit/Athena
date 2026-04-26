# P5 PR-149 fixup + PR-150 — Notification Tx Fixup & Phase 5 Mocked Chunk-Load Bypass

## Date
2026-04-26

## Scope

Two coordinated commits this turn:

1. **`11809e3` — PR-149 fixup**: one-line `throws Exception` so the
   isolation test added in PR-149 actually compiles. Backend Verify
   on `7a9d65e` failed at javac with `unreported exception
   java.io.IOException; must be caught or declared to be thrown`.

2. **`9b81041` — PR-150**: roll out the validated PR-148 bypass
   pattern to the next page-agnostic Phase 5 Mocked spec
   (`app-error-boundary-chunk-load-recovery.mock.spec.ts`, 2 tests).

Both are independent of each other: one is a tiny test-file compile
fix on the backend, the other is a parallel Phase 5 Mocked rollout
on the frontend e2e. Shipping together because the cadence stayed
the same.

## PR-149 fixup detail (`11809e3`)

PR-149's `runScheduledDeliveriesNowIsolatesPerPresetFailures` test
calls `uploadService.uploadDocument(...)` which throws
`IOException`. Mockito's `when(...).thenReturn(...)` doesn't avoid
declaring checked exceptions thrown by the *call* — javac still
sees the call and demands the test method declare or catch.

```diff
-    void runScheduledDeliveriesNowIsolatesPerPresetFailures() {
+    void runScheduledDeliveriesNowIsolatesPerPresetFailures() throws Exception {
```

Other tests in the same file use exactly this pattern
(`deliverNowUploadsCsvAndRecordsExecution`, etc.). One-line, no
behaviour change.

## PR-150 detail (`9b81041`)

### Validation that the approach works

PR-148's CI run (`24939746974`) showed the noise-filter tests
moving from "failing 3× at 1.1m each" to "passing in <1s":

```
✓ 11 [chromium] › app-error-boundary-noise-filter.mock.spec.ts:15:5 ›
       App error boundary: ignores ResizeObserver global error noise
       (mocked) (732ms)
✓ 12 [chromium] › app-error-boundary-noise-filter.mock.spec.ts:31:5 ›
       App error boundary: ignores abort-like unhandled rejection
       noise (mocked) (693ms)
```

That confirms the Option A approach works. Roll out to the next
spec where it's safe.

### Why chunk-load-recovery is page-agnostic

The two tests in `app-error-boundary-chunk-load-recovery.mock.spec.ts`
exercise:
- The global `unhandledrejection` listener catching a thrown
  `ChunkLoadError` and showing the asset-refresh recovery hint
- Clicking "Reload" appending `_ecm_reload=<ts>` to the URL and
  the cleanup script (PR-93) stripping it after reload

Both behaviours are global window-level listeners; the host page
the user is on at the moment of the listener firing is incidental
to what's under test. Same as noise-filter.

### Fix shape (for both tests)

```diff
- await page.goto('/login', { waitUntil: 'domcontentloaded' });
- await expect(page.getByText('Sign in with your organization account'))
-   .toBeVisible({ timeout: 60_000 });
+ await seedBypassSessionE2E(page, 'admin', 'e2e-token');
+ await page.goto('/', { waitUntil: 'domcontentloaded' });
+ await expect(page.getByRole('button', { name: 'Account menu' }))
+   .toBeVisible({ timeout: 60_000 });
```

For the cache-bust test specifically, the post-reload assertion
also changes from "Sign in..." to "Account menu" because the
bypass session persists in localStorage across reload.

### What's preserved

- Test names — chunk-load behaviour is the subject under test
- The `recovery_event:chunk_load_hint_shown` and
  `recovery_event:chunk_load_reload_cache_bust` console-log markers
  (used by `phase5-regression.sh`'s recovery-events expectation)
- `test.setTimeout(120_000)` budget
- All inner `expect.toBeVisible` timeouts
- The dispatch of `unhandledrejection` with the `ChunkLoadError`
  reason, which is what triggers the global listener
- The `Reload` and `back to login` button assertions
- The `_ecm_reload=<ts>` append-then-strip mechanism (PR-93)

## Expected CI signal

| Job | Expected on `9b81041` |
|-----|------------------------|
| Backend Verify | ✅ — PR-149 fixup makes test compile, all unit tests pass |
| Frontend Build & Test | ✅ unchanged |
| Phase C Security | ✅ unchanged |
| Acceptance Smoke | ✅ unchanged |
| **Frontend E2E Core Gate / notification step** | **✅ expected** — PR-149's REQUIRES_NEW isolation should land the lane green |
| **Phase 5 Mocked Regression Gate** | **4 fewer failing tests** (PR-148's 2 + PR-150's 2). Job may still cancel due to remaining failures (app-error-boundary-recovery, bootstrap-startup-fallback, admin-audit-filter-export :6), but visible per-test progress |

If the notification gate goes green:
- PR-150 completes the next-step recommendation from yesterday's plan
- PR-151 follow-up: bootstrap-startup-fallback (mixed strategy)
- PR-152 follow-up: app-error-boundary-recovery (Keycloak `page.route` mock — preserves unauth /login flow)
- After all 4: lane closeout flip from `pending` to `accepted`
- After that: PR-153+ opens the email delivery channel

If the gate is still red, the controller-boundary catch + handler
from PR-145/146 will surface the next failure mode in
`ApiError.message`. Diagnostic surface is now load-bearing.

## Files Changed

| File | Lines |
|------|-------|
| `ecm-core/.../service/RmReportPresetDeliveryServiceTest.java` | +1 / -1 (PR-149 fixup) |
| `ecm-frontend/e2e/app-error-boundary-chunk-load-recovery.mock.spec.ts` | +20 / -5 (PR-150) |

No backend production code change. No migration. No frontend
component change.

## Sequencing

| PR | Role | Status |
|----|------|--------|
| PR-145 | Diagnostic catch + handler | ✅ shipped |
| PR-146 | Tx workaround + controller-boundary catch | ✅ shipped |
| PR-148 | Phase 5 Mocked PoC (noise-filter) | ✅ shipped + **CI-validated** |
| PR-149 | Per-preset REQUIRES_NEW isolation via self-injection | ✅ shipped |
| **PR-149 fixup** | **`throws Exception` on isolation test** | **✅ shipped this turn** |
| **PR-150** | **Bypass rollout to chunk-load-recovery (2 tests)** | **✅ shipped this turn** |

## Non-goals

- Did not touch `app-error-boundary-recovery.mock.spec.ts` — its
  semantic intent IS the unauth /login → back-to-login flow.
  Bypass is wrong; that test needs a Keycloak `page.route` mock
  (planned PR-152).
- Did not touch `bootstrap-startup-fallback.mock.spec.ts` —
  per-test analysis still pending (planned PR-151).
- Did not touch `admin-audit-filter-export.mock.spec.ts:6` —
  already uses bypass; its 32.5s failure has a different cause
  that warrants its own investigation slice.
- Did not change any production code on the frontend.

## Memory entry (for the future)

When the rollout completes (likely PR-152 or PR-153), update
memory with the bypass-vs-keycloak-mock decision rule:

> A failing Phase 5 Mocked spec that navigates to `/login` first
> usually has one of two shapes: (a) page-agnostic global behaviour
> under test, in which case bypass + authenticated route is the
> right fix; (b) the unauth /login flow IS the subject under test
> (e.g., "back to login" recovery, startup fallback overlay), in
> which case a `page.route('**/realms/**', ...)` mock that
> short-circuits Keycloak is the right fix.
