# P5 Phase 5 Mocked Decision Rule — Memory + CI Hold

## Date
2026-04-26

## Scope

Smallest meaningful deliverable for this turn. Captures the
bypass-vs-Keycloak-mock decision rule from PR-148 → PR-152 as a
session memory entry so future test additions don't relearn the
same pattern.

No code change. No test change. Memory only — outside the git tree
in `.claude/projects/-Users-chouhua-Downloads-Github-Athena/memory/`.

## Why this is the right slice this turn

Six CI runs are in flight (`9b81041`, `b57ff31`, `4957c0b`,
`8410eaf`, `3708ba8`, `1c558b8`). All have Backend Verify ✅ and
Phase C ✅; all have Frontend Build still running. The decisive
verdicts (E2E Core Gate's notification step and the Phase 5 Mocked
gate) won't land for ~15-25 minutes.

Picking another code slice now would:

- Layer a sixth or seventh in-flight CI run on top of those six,
  spending CI budget without information return
- Create cross-slice verification debt — if a slice fails, it's
  unclear whether the failure is its own or a regression from
  something already in flight
- Bypass the project memory rule on closeout boundary respect
  (`project_rm_preset_delivery_closeout.md`)

The right move is a contained docs/memory deliverable that adds
value independent of any CI verdict.

## What landed this turn

### Memory entry (in session memory, not git)

`memory/feedback_phase5_mocked_keycloak_strategy.md`:

> **Decision rule** for new or rescued Phase 5 Mocked tests:
>
> | Test's semantic intent | Right fix |
> |------------------------|-----------|
> | Page-agnostic global behaviour — `/login` was just a "page is loaded" sanity check | `seedBypassSessionE2E` + `goto('/')` + assert `Account menu` |
> | Unauth `/login` flow IS the subject — "Back to Login" recovery, forced bootstrap fallback overlay | `mockKeycloakUnreachable(page)` + keep `/login` |
> | Test forces an additional E2E knob | Combine with whichever of the above keeps semantic intent |

`memory/MEMORY.md` index updated with the new entry.

The memory layer is loaded into every future session for this
project, so the next time someone (or Codex) writes a
`*.mock.spec.ts` that goes through `/login`, the strategy is one
lookup away.

### What's NOT in this turn

- **No code commit** — pre-existing code base is unchanged
- **No design + verification doc in the git tree** — this MD is the
  exception, summarising the memory entry plus the CI-hold
  rationale
- **No new test, helper, or migration**

## Pending CI verdicts being awaited

| Lane | Decisive job | Latest run with relevant fix |
|------|--------------|------------------------------|
| Notification (PR-149's REQUIRES_NEW isolation) | Frontend E2E Core Gate, "Run RM notification acceptance gate" step | `9b81041` (or any of the in-flight runs after `11809e3`) |
| Phase 5 Mocked rollout (PR-148 + PR-150 + PR-151 + PR-152) | Frontend E2E Phase 5 Mocked Regression Gate | `8410eaf` for the 7-test rollout, `3708ba8` adds the 8th |

When the in-flight CI lands:

1. **If notification gate ✅**: flip the lane closeout to `accepted`
   (single docs commit) and start PR-153 email lane
2. **If notification gate ❌**: read the `ApiError.message` field in
   the 500 body — the controller-boundary catch from PR-145/146
   guarantees a named cause — and land a targeted fix as PR-153
3. **If Phase 5 Mocked goes green**: update memory to demote the
   `feedback_es_facet_aggregation_race.md` gate-level implication
4. **If Phase 5 Mocked stays cancelled**: most likely
   `admin-audit-filter-export.mock.spec.ts:6` is still red (32.5s,
   different root cause); open a separate investigation slice.
   Need the artifact for that — Phase 5 Mocked needs to upload one,
   which only happens on cancel/failure post-job, so the wait
   produces the artifact

## Sequencing summary

| PR | Role | Status |
|----|------|--------|
| PR-148 | Phase 5 Mocked PoC (noise-filter, bypass) | ✅ shipped + CI-validated (732ms / 693ms passing) |
| PR-149 | Per-preset REQUIRES_NEW isolation | ✅ shipped + Backend Verify green; E2E pending |
| PR-149-fixup | `throws Exception` | ✅ shipped + Backend Verify green |
| PR-150 | Chunk-load bypass | ✅ shipped; Phase 5 Mocked pending |
| PR-151 | Bootstrap-startup-fallback (mixed strategy + helper) | ✅ shipped; Phase 5 Mocked pending |
| PR-152 | App-error-boundary-recovery (Keycloak mock) | ✅ shipped; Phase 5 Mocked pending |
| **This turn** | **Memory entry + CI hold** | **✅ shipped (memory only, no git commit)** |

## Files Changed

- `memory/feedback_phase5_mocked_keycloak_strategy.md` (new, 38 lines)
- `memory/MEMORY.md` (one-line index update)

Outside the git tree. No CI impact.

## Next-turn behaviour

When the user sends the next "请继续":

- If the decisive CI verdicts are in: act on them per the matrix
  above
- If still in flight: hold rather than start new code work, and
  surface the in-flight status with a wakeup if necessary
- If the user explicitly asks for a new slice (independent of CI):
  start PR-153 (admin-audit-filter-export investigation, or email
  lane if the notification gate has gone green)
