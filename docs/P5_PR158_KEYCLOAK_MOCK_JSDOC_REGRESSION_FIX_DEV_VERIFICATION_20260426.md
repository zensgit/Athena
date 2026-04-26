# P5 PR-158 — keycloakMock JSDoc Self-Termination Regression Fix

## Date
2026-04-26

## Scope

Critical one-file fix to `ecm-frontend/e2e/helpers/keycloakMock.ts`.
PR-155's JSDoc block contained a glob pattern with embedded `*/` that
prematurely terminated the comment, leaving dangling identifiers
that threw `ReferenceError: realms is not defined` at module import
time. Every spec that imported the helper failed to load.

E2E test helper only. No backend, no production code, no test
spec change.

## What CI showed (the smoking gun)

Reading `beca1cf`'s Phase 5 Mocked artifact (PR-155 abort fix
commit), the playwright `report.json` revealed:

```json
{
  "stats": {
    "total": 0,
    "expected": 0,
    "unexpected": 0,
    "flaky": 0,
    "skipped": 0,
    "ok": true
  }
}
```

**Zero tests ran.** The job's duration was ~30 seconds (vs the
normal ~30 minutes for a full Phase 5 Mocked run) — clear
fingerprint for module-load failure.

The job log named the cause exactly:

```
ReferenceError: realms is not defined
   at helpers/keycloakMock.ts:8
   8 |  * **Aborts** every `**/realms/**` request — XHR, fetch, **and**
                         ^

   at Object.<anonymous> (/...../helpers/keycloakMock.ts:8:25)
   at Object.<anonymous> (/...../app-error-boundary-recovery.mock.spec.ts:2:1)
```

Same trace from `bootstrap-startup-fallback.mock.spec.ts:2:1`, etc.
The helper threw at module import time; every importing spec failed
to load.

## Root cause

PR-155's JSDoc block:

```
/**
 * Short-circuit Keycloak network calls...
 *
 * **Aborts** every `**/realms/**` request - XHR, fetch, **and**
 *                       ^^                              ^
 *                  this is the early-close marker
 ...
```

The `**/realms/**` text contains `*/`. The JSDoc parser sees:
1. `/** ` — open
2. `... text ... **` — bold marker
3. `/realms/` — text
4. `**` — end bold
5. **`*/`** — comment ends right here (any `*/` closes the block)
6. `realms/**` — code: identifier `realms`, then `/**` starts a new comment

Step 5 was the bug. Once the comment closed at the embedded `*/`,
"realms" was a free reference. Module load → `ReferenceError`.

Critically, `tsc --noEmit` passes! TypeScript's compiler handles
JSDoc parsing differently from Playwright's tsx loader. The error
only surfaced at runtime in CI.

## Design

Replace the entire JSDoc block with line `//` comments. Line
comments have no terminator and can contain arbitrary text
including `*/`:

```diff
- /**
-  * Short-circuit Keycloak network calls so static-serve e2e specs...
-  *
-  * **Aborts** every `**/realms/**` request — XHR, fetch, **and**
-  ...
-  */
+ // Short-circuit Keycloak network calls so static-serve e2e specs
+ // (Phase 5 Mocked Regression Gate) don't hang waiting for a Keycloak
+ // server that doesn't exist in their environment.
+ //
+ // Aborts every "/realms/" request - XHR, fetch, and top-level...
  export async function mockKeycloakUnreachable(page: Page): Promise<void> {
    await page.route('**/realms/**', async (route) => {
```

The actual `**/realms/**` glob inside `page.route(...)` is a string
literal — entirely safe. Only the JSDoc free-form text was the
problem.

The new comment block also includes a prominent warning section
about why JSDoc can't be used here, so a future revert to JSDoc
would catch the issue at code-review time.

## Why this is critical

CI evidence shows that ALL Phase 5 Mocked tests using the helper
have been failing for the last several CI rounds:

- `bootstrap-startup-fallback.mock.spec.ts` (PR-151 added import)
- `app-error-boundary-recovery.mock.spec.ts` (PR-152 added import)
- `route-fallback-no-blank.mock.spec.ts` (PR-156 added import)
- `startup-visibility-sla.mock.spec.ts` (PR-156 added import)
- `app-error-boundary-chunk-load-recovery.mock.spec.ts` (PR-157 added import to test :38)

That's **8+ test cases** that have been red since PR-155 not because of
the rollout's logic but because of a comment-parsing accident.

The Phase 5 Mocked failure verdicts I documented in earlier MDs
(PR-156, PR-157 design docs) were thus reading the **wrong** cause.
The bypass-vs-mock decision rule is correct; the JSDoc bug just
masked everything.

## Memory entry shipped

`memory/feedback_jsdoc_glob_terminator.md` (new):

> A JSDoc /** ... */ block that contains a glob like `**/foo/**` or
> any string with `*/` self-terminates at the `*/` inside the glob,
> leaving subsequent identifiers dangling as runtime ReferenceErrors
> at module-import time.
>
> Fingerprint: Playwright report.json with `total: 0, ok: true` and
> a job duration far below normal. The suite never started; specs
> failed at import.
>
> Fix: use `// line comments` for module docs that mention globs;
> if JSDoc is required, escape with `*\/` or rewrite the glob.

`MEMORY.md` index updated.

## Verification

### Local
- `npx -p typescript@5.4.5 tsc --noEmit` — clean (note: this
  doesn't catch the bug, as documented in the memory entry)
- `npm run lint` — clean

### Expected CI signal on `9ad9047`

| Job | Expected |
|-----|----------|
| Backend Verify | ✅ unchanged |
| Frontend Build & Test | ✅ unchanged |
| Phase C Security | ✅ unchanged |
| Acceptance Smoke | ✅ unchanged |
| Frontend E2E Core Gate | ✅ unchanged (this helper is Phase 5 Mocked only) |
| **Phase 5 Mocked Regression Gate** | **The 8+ tests that have been silently failing should finally execute** — actual results will reveal whether the rollout's logic (PR-148/150/151/152/155/156/157) actually works |

## Why ship this with high urgency

For ~3 CI rounds (`beca1cf`, `6200639`, `aa2de6d`), I've been
shipping rollout extensions believing they were the fix while the
underlying helper was broken. The diagnostic-cadence memory entry
warns against speculation; this regression escaped because the
"30-second job duration" / "0 tests" pattern was a new symptom not
covered by the existing diagnostic pattern.

Memory entry `feedback_jsdoc_glob_terminator.md` adds this
fingerprint to the diagnostic toolkit.

## Files Changed

| File | Lines |
|------|-------|
| `ecm-frontend/e2e/helpers/keycloakMock.ts` | +36 / -32 |

No production code change.

## Sequencing

| PR | Role | Status |
|----|------|--------|
| PR-145..149 | Notification lane structural fix | ✅ — gate green on multiple runs |
| PR-150 | Phase 5 Mocked: chunk-load (bypass) | ✅ — :13 confirmed passing |
| PR-151 | Phase 5 Mocked: bootstrap (mixed) + helper | ⚠️ helper bug shadowed results |
| PR-152 | Phase 5 Mocked: recovery (mock) | ⚠️ same |
| PR-153 | Inner-loop INFO logs | ✅ residual |
| PR-154 | Locator strict-match | ✅ — confirmed deterministic |
| PR-155 | Helper: fulfill → abort + JSDoc bug | ⚠️ helper now broken |
| PR-156 | route-fallback + SLA (mock) | ⚠️ shadowed |
| PR-157 | chunk-load :38 → mock | ⚠️ shadowed |
| **PR-158** | **JSDoc bug fix** | **✅ this turn — unblocks PR-151..157 verdicts** |

## What we will know after `9ad9047` CI

The 8+ shadowed tests will finally execute. Possible outcomes:

| Outcome | Action |
|---------|--------|
| All 8+ pass | Phase 5 Mocked may have its first-ever fully-green run; flip notification lane to accepted; start email lane |
| Some pass, some fail | The remaining failures will name new layers (e.g., the test-internal logic bugs) that PR-159+ targets |
| Tests load but suite still cancels at 30 min | Indicates additional hangs we haven't accounted for; new investigation slice |
| Same "0 tests, 30s, ok:true" pattern | The JSDoc fix wasn't enough; look for another similar issue in another helper |

Most likely scenario based on the diagnosis: the rollout works,
some tests pass cleanly, a couple may need additional small fixes,
and Phase 5 Mocked transitions from "always cancels" to "completes
with N residual failures" — finally a measurable status.

## Non-goals

- Did not revert any of the rollout commits — they're correct in
  intent; the helper was the only broken layer
- Did not touch any test spec — same usage pattern, same import
  shape; only the helper's comment block changed
- Did not change Phase 5 Mocked workflow — same job config

## Lessons learned

1. **`tsc --noEmit` is not sufficient** for Playwright e2e helpers
   — its JSDoc parsing differs from the runtime tsx loader's
2. **Job duration is a load-time signal** — a normally-30min job
   completing in 30s is almost always import-time module failure
3. **Playwright `report.json` `total:0 ok:true` is diagnostic-grade
   evidence** — when no tests run, the suite never started, and
   "ok:true" is a tautology
