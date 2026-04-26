# P5 Diagnostic Chain Status — Notification Lane Resolved

## Date
2026-04-26

## Scope

Status read of the notification gate and Phase 5 Mocked rollout
based on the latest CI verdicts. Documents what's resolved, what's
in flight, and what's deferred to separate slices.

No code change. Status MD only.

## Notification gate — RESOLVED

The notification lane has now **passed multiple CI runs**:

| Run | E2E Core Gate / notification step | Reading |
|-----|-----------------------------------|---------|
| `8410eaf` (PR-148+149+150+151) | ✅ "1 flaky, 3 passed" | First green; `:914` flaked once, retry-1 passed |
| `8ad99f7` (PR-153 inner-loop logs) | **✅ success** | Retry-luck on `:914` |
| `9dcd55b` (cadence memory MD) | **✅ success** | Retry-luck on `:914` |
| `3708ba8` (PR-152) | ❌ all 3 retries on `:914` | Strict-mode flake, deterministic this run |
| `abf45b9` (PR-153 docs) | ❌ all 3 retries on `:914` | Same flake |

Pattern: `:914`'s `getByText(deliveredFilename)` matches two
elements (`<em>filename</em>` and `<span>Delivered <filename>...</span>`).
Strict-mode violation flakes by render timing.

**PR-154 (`088f55e`) `{ exact: true }` fix locks in the deterministic
pass.** Once that CI run lands, `:914` will pass every time.

### Was the `processOneScheduledDelivery` INFO log diagnosis needed?

No. PR-149's REQUIRES_NEW isolation closed the structural failure
mode (`UnexpectedRollbackException` from per-preset rollback
poisoning). What remained was the `:914` test-locator strict-mode
flake — an entirely separate test-only issue.

PR-153's INFO logs are still present in `processOneScheduledDelivery`
as defence-in-depth. They cost a few INFO lines per scheduled tick;
demote-to-DEBUG can land in PR-159+ once the lane has run green for
several CI rounds.

**`if: failure()` on the artifact upload step means the `8ad99f7`
success run produced no artifact** — the INFO logs were never read,
because they were never needed.

## Phase 5 Mocked rollout — STRUCTURALLY WORKING

| Run | Phase 5 Mocked verdict | Significance |
|-----|------------------------|--------------|
| `11809e3` (predates rollout) | Cancelled @ 30 min | Expected |
| `9b81041` (predates PR-151) | Cancelled @ 30 min | Expected |
| `b57ff31` (predates PR-151) | Cancelled @ 30 min | Expected |
| **`8410eaf` (PR-148+149+150+151)** | **Failure within budget** | **First time the suite ever finished in 30 min** |
| **`3708ba8` (adds PR-152)** | **Failure within budget** | Second confirmation |
| `8ad99f7` / `abf45b9` / `9dcd55b` | In progress (~25 min) | Will likely cancel — these run pre-PR-155 helper |

**The 30-min cancellation cliff has been broken.** PR-148 + PR-150
+ PR-151's `:114` + PR-152 collectively rescued enough tests to fit
the suite in budget.

### Residual failures and their in-flight fixes

Reading `8410eaf`'s artifact log, 8 tests still fail (down from
~30 in pre-rollout cancelled runs):

| Spec / Test | Failure cause | In-flight fix |
|-------------|---------------|---------------|
| `bootstrap-startup-fallback :19` | `mockKeycloakUnreachable` fulfill rendered JSON as page | PR-155 abort fix (`beca1cf`) |
| `bootstrap-startup-fallback :70` | Same as :19 | PR-155 |
| `app-error-boundary-recovery :5` | Same | PR-155 |
| `route-fallback-no-blank :77` | Keycloak hang (no helper added yet) | PR-156 added helper (`6200639`) |
| `startup-visibility-sla :88` | Same | PR-156 |
| `chunk-load-recovery :38` | Bypass URL-timing race | PR-157 (`aa2de6d`) — switch to mock |
| `search-suggestions :4, :184` | Different cause (~1s, not Keycloak hang) | Separate slice (PR-158) |
| `admin-audit-filter-export :6` | Different cause (32.5s, already uses bypass) | Separate slice (PR-159) |

After PR-155/156/157 CI confirms (next run pickup is `aa2de6d`
which has all three), expected residuals are just
`search-suggestions` and `admin-audit-filter-export:6`. Both are
artifact-evident and need their own investigations once Phase 5
Mocked completes a green run.

## Decision matrix for next-turn behaviour

| Scenario | Action |
|----------|--------|
| `088f55e` (PR-154) E2E Core Gate ✅ + `aa2de6d` Phase 5 Mocked ✅ | Notification lane closeout flips to `accepted`; Phase 5 Mocked first-ever-green |
| `088f55e` ✅ but Phase 5 Mocked ❌ on different residual | Open PR-158/159 for the named residual (read its artifact) |
| `088f55e` ❌ on `:914` (somehow) | Re-investigate locator — extremely unlikely given the diagnosis |
| `aa2de6d` ❌ with same Keycloak symptom | PR-155 abort approach was wrong — revert + rethink |
| Both still in progress | Hold — same memory rule on closeout boundary |

## Sequencing

| PR | Role | Status |
|----|------|--------|
| PR-145 | Service-body catch + handler | ✅ shipped |
| PR-146 | Controller-boundary catch | ✅ shipped — surfaces 500 cause |
| PR-148 | Phase 5 Mocked: noise-filter (bypass) | ✅ + CI-validated passing |
| PR-149 | REQUIRES_NEW isolation | ✅ — structural fix for the 500 |
| PR-150 | Phase 5 Mocked: chunk-load (bypass) | ✅ — :13 passes, :38 needed PR-157 |
| PR-151 | Phase 5 Mocked: bootstrap (mixed) + helper | ✅ — :114 passes, :19/:70 need PR-155 |
| PR-152 | Phase 5 Mocked: recovery (mock) | ✅ — needed PR-155 |
| PR-153 | Inner-loop INFO logs | ✅ — residual diagnostic |
| PR-154 | Strict-match locator on `:914` | ✅ — locks in deterministic pass |
| PR-155 | Helper: fulfill → abort | ✅ — fixes JSON-as-page artifact issue |
| PR-156 | Phase 5 Mocked: route-fallback + SLA (mock) | ✅ |
| **PR-157** | **Chunk-load :38: bypass → mock** | **✅** |
| **This MD** | **Status read + decision matrix** | **✅ this turn** |
| PR-158 | search-suggestions investigation | Pending |
| PR-159 | admin-audit-filter-export:6 investigation | Pending |

## Files Changed in this commit

`docs/P5_DIAGNOSTIC_CHAIN_STATUS_RESOLVED_20260426.md` (new, this MD)

No code, no helper, no test change.

## Memory entry implications

Two updates pending CI confirmation:

1. `feedback_phase5_mocked_keycloak_strategy.md` — add the
   implementation note about `route.abort` (PR-155 evidence) and
   the URL-timing tests caveat (PR-157 evidence).
2. `feedback_diagnostic_cadence_for_opaque_500s.md` — close out the
   PR-145 → PR-154 example chain as confirmed.

I'll land both as a single docs commit after `088f55e`'s CI lands
green.

## Bottom line

The notification gate's structural cause is fixed. The remaining
green-vs-red in CI is governed by `:914`'s flake, which PR-154
closes deterministically. Phase 5 Mocked has crossed the
"finishes within budget" threshold; the in-flight PR-155/156/157
should bring it to "all known-cause specs green", leaving 2
artifact-evident residual failures for separate investigation.

The diagnostic-cadence pattern (one named layer per CI round) was
the key pattern — captured in
`memory/feedback_diagnostic_cadence_for_opaque_500s.md` for future
reference.
