# P5 Next Development Plan — Revised

## Date
2026-04-26

## Why a revision

Yesterday's plan (`P5_NEXT_DEVELOPMENT_PLAN_20260425.md`) assumed the
PR-134 series would open the email delivery channel. Codex has spent
PR-134..144 hardening the **same notification CI gate** instead, and
the lane is **still not accepted** — every CI run on `4653f3e`,
`9b2d5f1`, and `40fe875` has failed.

This revision corrects the assumption, surfaces the actual blocker,
and re-sequences priorities accordingly.

## 1. What I found this turn

### CI was red for a different reason than I thought

The Frontend E2E Core Gate has been failing on every push since
`4653f3e` with:

```
bash: scripts/p5-rm-notification-acceptance-gate.sh: No such file or directory
```

`PR-132` modified `.github/workflows/ci.yml` to call two new scripts,
but the scripts themselves were sitting **untracked in the local
working tree** — they never made it onto `main`. Every CI run since
has died at the same line. The earlier "ES facet flake" diagnosis was
incomplete; the missing-script error came first.

### PR-134..144 were not the email lane

Eleven Codex slices delivered between 2026-04-25 and 2026-04-26 all
sit inside the notification lane. Summary:

| PR | Type | Purpose |
|----|------|---------|
| PR-134 | Hardening | Force due-state after schedule save (no near-future cron waits) |
| PR-135 | Hardening | Backend Surefire reports in `frontend_e2e_core` failure artifacts |
| PR-136 | Hardening | Contextual API diagnostics replace bare `response.ok()` asserts |
| PR-137 | Test | People service contract tests for single-preference RPCs |
| PR-138 / 140 | Test | RM page rollback tests for failed preference saves |
| PR-139 | Doc | CI observation evidence required before lane promotion |
| PR-141 | Script | `p5-rm-notification-closeout-preflight.sh` |
| PR-142 | Script | Preflight failure diagnostics |
| PR-143 | Test | Codified `notifyOnSuccess=true / notifyOnFailure=true` defaults |
| PR-144 | CI | Preflight runs in cheap frontend job before expensive live gate |
| PR-144A | CI | Preflight verifies CI workflow wiring semantics and artifact retention |
| PR-144B | CI | Preflight verifies the four required acceptance scenario titles |
| PR-144C | CI | Preflight verifies the five backend targeted test classes |
| PR-144D | CI | Preflight portability fix after GitHub runner lacked `rg` |

This is **the same notification lane**, hardened. Not a new capability.

### Operator-visible default flip

`P5_PR123_PR133_NOTIFICATION_LANE_INTEGRATION_VERIFICATION_20260425.md`
was updated this turn — `notifyOnSuccess` default flipped from
`false` → `true`. Operators now receive both success **and** failure
notifications by default. That's now codified in a contract test
(PR-143) so the default cannot drift silently.

### What I just shipped (`122c9ca`)

The PR-134..144 bundle plus the two missing scripts and the workflow
update have been committed and pushed. CI is now in flight on this
SHA. The next 15-30 minutes will tell whether the notification lane
gate finally goes green.

Post-revision local hardening adds `PR-144A`: the closeout preflight
now fails if the CI workflow drops the fast preflight, live acceptance
gate, backend Surefire artifacts, or required step ordering. This does
not change the P0 rule: wait for live CI acceptance before starting
`PR-145`.

`PR-144B` adds one more local guard: the preflight now checks that the
four required notification acceptance scenarios are still present by
title, not just that four tagged tests exist. This is still P0
hardening, not a new product lane.

`PR-144C` extends the same preflight to the backend half of the live
gate: all five required targeted test classes must still be listed in
the acceptance gate script and present under `ecm-core/src/test/java`.

`PR-144D` fixes the first real failure observed after network access
returned: GitHub Actions run `24935937705` failed in the fast
preflight because the runner did not have `rg`. The preflight now uses
only `awk`, `grep`, and `find`.

Post-`PR-144D`, the lane exposed runtime transaction-boundary issues
instead of CI wiring issues:

| PR | Type | Purpose |
|----|------|---------|
| PR-145 / PR-146 | Diagnostic + transaction | Surface `run-scheduled-deliveries` commit-time exceptions at the controller boundary |
| PR-149 | Transaction | Run each due preset through a self-injected `REQUIRES_NEW` worker |
| PR-149B | Transaction | Prevent optional missing-preference reads and direct owner notification publication from marking the per-preset transaction rollback-only |
| PR-148 / PR-150 | Parallel Phase 5 Mocked | Start reducing unrelated mocked-gate failures without changing notification semantics |
| PR-154 | E2E locator | Fix the final default-on success notification strict locator |
| PR-155 | Closeout | Promote the owner-inbox notification lane to accepted after CI gate success |

## 2. Current state

| Lane | Status |
|------|--------|
| RM preset-delivery core (PR-95..122) | **Closed (accepted)** |
| Notification lane runtime (PR-123..155) | **Closed (accepted)** |
| Notification lane CI/tx hardening (PR-134..155) | **Closed (accepted)** |
| Phase 5 Mocked gate | **Active CI-red lane**; PR-148/150/151/152 reduced some Keycloak/login hangs, but failures remain |
| Email delivery channel | **Not started**; should wait until the active mocked-gate red lane is contained or explicitly accepted as advisory |

## 3. Priority 0 — Notification lane accepted

This prerequisite is now satisfied.

GitHub Actions run `24947642547` on commit `7f3cb44` passed:

- `Backend Verify`
- `Frontend Build & Test`
- `Phase C Security Verification`
- `Acceptance Smoke (3 admin pages)`
- `Run RM notification acceptance gate`
- `Run core E2E gate`

## 4. Priority 1 — Phase 5 Mocked Regression Gate

Phase 5 Mocked is now the visible CI-red lane. Do not start the email
delivery channel until this lane is either green or explicitly
classified as advisory with documented ownership.

Recommended split:

| Slice | Content | Estimate |
|-------|---------|----------|
| PR-156 | Fix strict locator failures with scoped/exact locators, starting with `search-suggestions-save-search.mock.spec.ts` | 0.5 d |
| PR-157 | Finish Keycloak-unreachable mocks for `/login` semantic tests that still hang | 0.5-1 d |
| PR-158 | Investigate `admin-audit-filter-export.mock.spec.ts:473` separately; do not hide it behind retries | 0.5-1 d |
| PR-159 | Decide whether any remaining mocked-gate failures are product bugs or advisory infra noise | 0.5 d |

## 5. Priority 2 — Email delivery channel

| PR | Content | Estimate |
|----|---------|----------|
| PR-160 | Backend `EmailNotificationService` + `EmailTemplate` entity + Liquibase migration + `spring-boot-starter-mail` dep | 1-2 d |
| PR-161 | `NotificationChannel` abstraction; `InboxChannel` (current) + `EmailChannel`; fan-out per user preference | 1 d |
| PR-162 | New preference key `notifyBy.email` (default `false`); UI toggle on the RM preset card / Schedule dialog | 0.5 d |
| PR-163 | Wire `rm.report_preset.delivery.*` events into the email channel; mocked + full-stack smoke | 1-2 d |
| PR-164 | CI: isolated **Email Channel Gate** job (mirrors PR-132/144 pattern: cheap preflight + expensive live gate) | 0.5 d |

**Total: 4-6 days.**

**Strict gate: do not start until Phase 5 Mocked is no longer a
systemic CI-red lane, or until it is explicitly accepted as advisory.**
Otherwise the verification boundary will blur again.

## 6. Priority 3 (parallel-safe)

### A. Notification lane post-acceptance polish

Per PR-139's evidence checklist, after the lane is accepted:

- Backfill any missing tagged-test discovery edge cases
- Promote the `disabled-preference full-stack smoke tests` from
  "added and discoverable, with live execution pending" to
  "live execution observed"
- Each is ≤ 0.5 day

### B. Webhook / SLO / delegation (only after email ships)

Build on the `NotificationChannel` abstraction PR-146 will establish.

| Capability | Effort | Dependency |
|------------|--------|------------|
| Webhook channel | 1-2 d | PR-146 |
| Per-preset preference overrides | 1-2 d | Standalone |
| SLO alerting / escalation | ~1 wk | Threshold definition |
| Cross-owner delegation | ~1 wk | Authz model design |

## 7. Priority 4 — Phase-1 plan residuals

Unchanged from yesterday's plan. **Independent lanes, do not parallelise
with the email lane**:

| Item | Size |
|------|------|
| #3 Scheduled User Actions API surface | S (3-5 d) |
| #7 Site Invitation Workflow | M (1-2 w) |
| #1 Smart/Virtual Folders runtime | M (1-2 w) |
| #2 Legal Holds | L (2-4 w) |

## 8. Lessons from this revision

Three things to carry forward:

1. **A workflow change that calls a new script must commit the script
   in the same change.** Splitting them means CI fails in a way that
   looks like a flake but is actually a missing dependency. Add a
   pre-push reminder mentally.
2. **Codex's hardening loops are real.** PR-134..144 spent 11 slices
   on the same lane's CI signal. When a forecast assumes Codex will
   pick a specific named follow-up (like email), be ready for the
   actual outcome to be more polish on the prior lane. Don't write
   the next plan around named PR numbers.
3. **The CI red on docs-only commits was a real signal**, not a
   flake. The `9309295` and `7938d2f` failures pinned the missing
   script — I should have diagnosed at that point, not blamed the
   ES facet race. Memory entry `feedback_reread_code_before_regression_claim.md`
   already covers re-reading; this turn's miss was the inverse:
   *under-reading* the failure and assuming flake.

## 9. Recommended `继续` behaviour

Re-stated for the accepted notification state:

| Current state | Action |
|--------------------|--------|
| Notification lane accepted | Work Phase 5 Mocked red lane next |
| Phase 5 Mocked green or advisory | Start email delivery channel |
| New notification regression appears | Stop and fix the regression before email |
| CI still in progress | Wait for the job that owns the current decision boundary |

## 10. Summary table

| Priority | Work | Estimate | Blocking? |
|----------|------|----------|-----------|
| **P0** | Notification lane accepted | Done | No |
| **P1** | Phase 5 Mocked red-lane fixes | 2-3 d | Yes, before new email capability |
| P2 | Email delivery channel (PR-160..164) | 4-6 d | After P1 |
| P3 | Webhook / preference overrides / SLO / delegation | 1-7 d each | After email |
| P4 | Phase-1 residuals (#3, #7, #1, #2) | S-L per item | Independent |
| P5 | Phase-2 / Phase-3 capabilities | TBD | Deferred |
