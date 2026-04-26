# P5 — Diagnostic Cadence Memory + PR-153 CI Hold

## Date
2026-04-26

## Scope

Smallest meaningful deliverable for this turn — capture the
diagnostic cadence pattern that ran across PR-145 → PR-153 as a
session memory entry, while CI verdicts on the in-flight runs are
still 15-25 minutes out.

No code change. No git-tree commit beyond this MD. Memory entry
lives in `.claude/projects/.../memory/`.

## Why this is the right slice this turn

- **PR-153 verdict pending**: `8ad99f7` and `abf45b9` are mid-CI;
  the diagnostic-logs commit is what will name which branch of
  `processOneScheduledDelivery` produces `processedCount: 0`. PR-154
  will be the targeted fix and depends entirely on that data.
- **Phase 5 Mocked verdict pending**: `11809e3`'s Phase 5 Mocked
  job has been running 20 minutes; the rollout (PR-148/150/151/152)
  is at decision time within the next ~10 min.
- **No Codex tree work**: confirmed via `git status`.
- **Pre-staging email lane (PR-155+) violates closeout rule**:
  `project_rm_preset_delivery_closeout.md` says wait until the
  notification lane flips to `accepted`.

Picking another code slice now would create cross-slice verification
debt. The honest answer to "what's parallel-safe" is **a memory
entry that captures hard-won learnings** so future similar lanes
don't relearn them.

## Memory entry shipped

`memory/feedback_diagnostic_cadence_for_opaque_500s.md`:

> When a live-stack e2e gate fails with `{"status":500,"error":"Internal
> Server Error"}` (no `message` field) and the artifact log is rotated,
> do **not** speculate at the fix. Layer diagnostic infrastructure first,
> then narrow one root cause per CI round.
>
> The chain that worked across PR-145 → PR-153:
>
> 1. **PR-145** — service-body catch + handler. Outcome: handler
>    wired, but body still empty. → Catch was outside reach.
> 2. **PR-146** — controller-boundary catch. Outcome: body now
>    carries `UnexpectedRollbackException`. → Named the cause.
> 3. **PR-146 NOT_SUPPORTED workaround**. Outcome:
>    `InvalidDataAccessApiUsageException`. → Workaround broke
>    `@Modifying(flushAutomatically)`.
> 4. **PR-149 REQUIRES_NEW isolation**. Outcome: 500 gone,
>    `processedCount: 0`.
> 5. **PR-153 INFO logs at every branch**. Outcome: pending.
> 6. **PR-154 targeted fix** at the branch the logs name.

`memory/MEMORY.md` index updated with the new entry.

## Diagnostic chain visualised

| Round | Slice | What landed | Next signal |
|-------|-------|-------------|-------------|
| 1 | PR-145 | Service-body catch + handler | "Catch wired but body still empty" → catch is outside reach |
| 2 | PR-146 | Controller-boundary catch | Body has `UnexpectedRollbackException: rollback-only` |
| 3 | PR-146 (workaround) | `@Transactional(NOT_SUPPORTED)` | Body has `InvalidDataAccessApiUsageException` (workaround removed needed tx) |
| 4 | PR-149 | Real fix: REQUIRES_NEW per-preset isolation | 500 gone; new shape is `processedCount: 0` |
| 5 | **PR-153** | **Inner-loop INFO logs** | **Pending — will name which branch returns 0** |
| 6 | PR-154 | Targeted fix | TBD |

Each round was 1 commit, ~15-30 lines of code, plus 1 docs commit.
Total wall clock: ~15-25 min CI per round. Cumulative effort more
than a one-shot guess fix, but every step produced verified
evidence.

## What success looks like after PR-153 lands

The artifact's `tmp/ci-e2e-logs/ecm-core.log` will contain INFO
lines like:

```
processDueScheduledDeliveries: now=... duePresetCount=N
processOneScheduledDelivery: enter presetId=... nextRunAt=... cron=...
processOneScheduledDelivery: claim CAS lost for presetId=...
                                   ↑ OR
processOneScheduledDelivery: calling deliverPreset for presetId=... folderId=...
processOneScheduledDelivery: deliverPreset threw for presetId=...: <class>: <message>
                                   ↑ OR
processOneScheduledDelivery: deliverPreset returned cleanly for presetId=...
```

The branch named there is what PR-154 fixes. Most likely: `claim
CAS lost`, due to JPA `LocalDateTime` precision differing from the
SQL `now() - interval '1 minute'` value. Fix would broaden the
CAS query to compare with a tolerance, or use `BETWEEN` instead of
`=`.

## Pending CI verdicts

| Run | Phase 5 Mocked | E2E Core Gate / notification step |
|-----|----------------|-----------------------------------|
| `11809e3` (PR-149 fixup) | Running 20 min | ❌ failed at `processedCount: 0` |
| `9b81041` (PR-150) | Running | ❌ same shape |
| `b57ff31` (PR-150 docs) | Running | ❌ same shape |
| `8ad99f7` (PR-153 logs) | Queued | Pending — verdict drives PR-154 |
| `abf45b9` (PR-153 docs) | Queued | Pending |

## Files Changed

- `memory/feedback_diagnostic_cadence_for_opaque_500s.md` (new, 47 lines)
- `memory/MEMORY.md` (one-line index update)
- `docs/P5_DIAGNOSTIC_CADENCE_MEMORY_AND_PR153_HOLD_20260426.md` (this MD)

Memory entries persist across sessions; the MD is the in-tree
record for this turn.

## Sequencing

| PR | Role | Status |
|----|------|--------|
| PR-145..146 | Diagnostic catch + handler + boundary catch | ✅ shipped |
| PR-148..152 | Phase 5 Mocked Keycloak-hang rollout | ✅ shipped (4 specs, 8 tests) |
| PR-149 + fixup | REQUIRES_NEW isolation | ✅ shipped |
| PR-153 | Inner-loop diagnostic logging | ✅ shipped + pending CI |
| **This turn** | **Memory entry + CI hold** | **✅ shipped (memory + MD)** |
| PR-154 | Targeted fix at the branch PR-153 names | Pending CI verdict |

## Non-goals

- Did not start PR-154 — that requires the artifact log from
  `8ad99f7`'s CI run, which is still pending
- Did not pre-stage the email lane (PR-155+) — closeout rule
  explicit
- Did not modify the test, the controller, the service, or any
  test helper
- Did not investigate the Phase 5 Mocked verdict (still in
  flight at the 20-min mark)

## Recommended next-turn behaviour

Same matrix as the previous CI-hold MD:

| `8ad99f7` outcome | Action |
|-------------------|--------|
| `processOneScheduledDelivery: claim CAS lost` | Land PR-154 = broaden the CAS query (e.g., compare timestamps with millisecond tolerance instead of strict equality) |
| `deliverPreset threw <class>: <message>` | Land PR-154 = targeted fix on the named class (depends on what's thrown) |
| `deliverPreset returned cleanly` then count=0 | Highly unlikely — would mean a logic bug in `processOneScheduledDelivery` or the loop counter. Read the code. |
| `duePresetCount=0` | The forced-due preset isn't visible to the scheduler's query — tx visibility / precision issue at a different layer |
