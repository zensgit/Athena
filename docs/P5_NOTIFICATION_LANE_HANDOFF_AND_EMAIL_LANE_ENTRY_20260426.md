# P5 — Notification Lane Handoff + Email Lane Entry (PR-159)

## Date
2026-04-26

## Scope

Closes the notification lane diagnostic chain (PR-145 → PR-158) with
final verdicts and lays out the entry point for the next capability —
email delivery channel (PR-159).

Docs only. No code change this commit.

## Notification lane — DONE

### Final CI evidence

| Run | E2E Core Gate / notification step |
|-----|-----------------------------------|
| `8410eaf` (PR-148+149+150+151) | ✅ "1 flaky, 3 passed" |
| `8ad99f7` (PR-153 logs) | ✅ success |
| `9dcd55b` (cadence MD) | ✅ success |
| **`088f55e` (PR-154 locator fix)** | **✅ success — first deterministic** |
| `9ad9047` (PR-158 JSDoc fix) | ✅ success |

The lane has now passed multiple consecutive CI runs with no `:914`
flake retry-luck dependency.

### Closeout status

Already flipped to `accepted` in commit `08f7b0e` ("docs(rm): P5
PR-155 accept notification lane closeout"). The closeout doc
references CI run `24947642547` on commit `7f3cb44` as final
evidence.

### What the diagnostic chain produced

| PR | Layer named | Evidence |
|----|-------------|----------|
| PR-145 | Service-body catch outside reach | Body still empty after wiring catch |
| PR-146 | `UnexpectedRollbackException` from outer-tx pollution | 500 body now has `class:message` |
| PR-146 (workaround) | `InvalidDataAccessApiUsageException` | NOT_SUPPORTED removed needed tx |
| PR-149 | REQUIRES_NEW per-preset isolation | 500 gone; `processedCount: 0` exposed (transient flake) |
| PR-153 | Inner-loop logs | Diagnostic insurance |
| PR-154 | `:914` strict-mode locator violation | Two elements matched substring |
| PR-158 | JSDoc `*/` self-termination in helper | `report.json` total:0, ok:true, ~30s job |

7 layers named. Each round small and reversible. The chain ran end
to end across ~24 hours of CI cycles. Memory entries
(`feedback_diagnostic_cadence_for_opaque_500s.md`,
`feedback_jsdoc_glob_terminator.md`) preserve the patterns for
future similar lanes.

## Phase 5 Mocked — pending decisive verdict

`9ad9047` (PR-158 JSDoc fix on top of PR-148/150/151/152/155/156/157
rollout) Phase 5 Mocked is still in progress at this writing.

What we'll learn from its verdict:

| Outcome | Meaning |
|---------|---------|
| Phase 5 Mocked ✅ green | First-ever-green for this gate. Rollout closed. Demote PR-153 INFO logs in PR-160. |
| 1-2 residual failures (search-suggestions / admin-audit-export) | Open PR-160/PR-161 as named investigations |
| 5+ residual failures | Some rollout assumption was wrong; revert PR-155/156/157 and reinvestigate |
| Same "0 tests, 30s, ok:true" pattern | Another helper has the same JSDoc issue; grep all e2e helpers for `*/` inside `/** */` |

## Email lane — PR-159 entry

Per the original plan (`docs/P5_NEXT_DEVELOPMENT_PLAN_REVISED_20260426.md`),
the email channel is the top deferred capability from PR-122
closeout and Phase-1 plan #5. Now that the notification lane is
accepted, this can start.

### Original 5-slice breakdown (renumbered after diagnostic chain)

| PR | Content | Estimate |
|----|---------|----------|
| **PR-159** | Backend `EmailNotificationService` + `EmailTemplate` entity + Liquibase migration + `spring-boot-starter-mail` | 1-2 d |
| PR-160 | `NotificationChannel` abstraction; `InboxChannel` (current) + `EmailChannel` impls; `EcmEventListener` fan-out per user preference | 1 d |
| PR-161 | New preference key `notifyBy.email` (default `false`); UI toggle on RM preset card / Schedule dialog | 0.5 d |
| PR-162 | Wire `rm.report_preset.delivery.*` events into the email channel; mocked + full-stack smoke | 1-2 d |
| PR-163 | CI: isolated **Email Channel Gate** job (mirrors PR-132/144 pattern: cheap preflight + expensive live gate) | 0.5 d |

**Total: 4-6 days.**

### PR-159 design preview (next slice content)

**Backend foundation:**

- New `EmailNotificationService` in `ecm-core/src/main/java/com/ecm/core/service/`
- New `EmailTemplate` entity (subject template + body template, Thymeleaf)
- New repository for templates
- New Liquibase migration `084-email-notification-foundation.xml`
- `pom.xml` adds `org.springframework.boot:spring-boot-starter-mail`
- Configuration: `application.yml` adds `spring.mail.*` with sane defaults overridable by env
- Initial Thymeleaf templates for the two existing activity events
  (`rm.report_preset.delivery.succeeded`, `...failed`)
- Unit tests for `EmailNotificationService.send(...)` with a mock
  `JavaMailSender`
- No frontend change in this slice
- No CI change in this slice (PR-163 adds the gate)

**Why backend-first:**

- Frontend already has `notifyBy.email` discoverable via the
  preference store; no UI controls until PR-161 wires them
- The `NotificationChannel` abstraction in PR-160 needs the email
  service to exist before it can dispatch to it
- Adding the dependency in `pom.xml` is a one-time touch; doing it
  in PR-159 means PR-160 onward doesn't have a build-config
  preamble

### Memory entries that apply

- `feedback_diagnostic_cadence_for_opaque_500s.md` — same pattern
  applies if email lane has surprises
- `project_rm_preset_delivery_closeout.md` — preset-delivery core
  is still closed; email layers ON TOP, doesn't reopen the core
- `feedback_phase5_mocked_keycloak_strategy.md` — apply to any new
  e2e specs added in PR-162
- `feedback_jsdoc_glob_terminator.md` — apply to any new helper

## Recommended next-turn behaviour

| `9ad9047` Phase 5 Mocked verdict | Action |
|-----------------------------------|--------|
| ✅ first-ever-green | Update memory entry: rollout pattern complete; demote PR-153 INFO logs in PR-160; start PR-159 |
| ❌ 1-2 residuals | Open PR-160 (search-suggestions) / PR-161 (admin-audit-export) investigations from artifacts |
| ❌ 5+ residuals | Revert PR-155/156/157, reinvestigate. Notification lane unaffected. |
| Still in progress | Hold until verdict. Do not pre-stage PR-159. |

The boundary rule from `project_rm_preset_delivery_closeout.md`:
**don't open the email lane (PR-159) until Phase 5 Mocked rollout
verdict is in.** Phase 5 Mocked is a *separate* gate from the
notification lane — but the rollout fixes ship in the same e2e
helpers, so they share the same verification surface.

## Files Changed

- `docs/P5_NOTIFICATION_LANE_HANDOFF_AND_EMAIL_LANE_ENTRY_20260426.md`
  (this MD)

No code, no test, no helper, no migration.

## Sequencing summary

| Group | PRs | Status |
|-------|-----|--------|
| Notification lane diagnostic chain | PR-145..PR-158 | ✅ closed; lane accepted |
| Phase 5 Mocked rollout | PR-148/150/151/152/155/156/157 + PR-158 fix | ⏳ verifying on `9ad9047` |
| Email lane | PR-159..PR-163 | 🚦 awaiting Phase 5 verdict before start |
| Phase 5 Mocked residuals | PR-160 / PR-161 (if needed) | After Phase 5 verdict |

## Bottom line

The notification lane chain is complete. The diagnostic-cadence
discipline produced a deterministic outcome through 7 named layers
with no speculative fixes — exactly as the memory entry codified.

Phase 5 Mocked is one CI verdict away from either "first-ever-green"
(rollout complete) or "named residuals" (open small investigation
slices). Once that lands, PR-159 starts the email lane.

The email lane is the original P1 from yesterday's plan, now
unblocked.
