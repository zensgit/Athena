# P5 Next Development Plan

## Date
2026-04-25

## Purpose

Concrete, actionable forward plan based on the current codebase state.
Reads the shipped PR-95..133 chains and Codex's own remaining-work
notes to propose the next sequence of slices, with explicit
priorities and gating.

## 1. Current State (tip = `9b2d5f1`)

| Lane | Range | Status |
|------|-------|--------|
| RM preset-delivery core | PR-95..122 | **Closed** — milestone closeout `PR-122` (docs-only) |
| Notification lane (owner inbox) | PR-123..133 | **Code pushed, awaiting CI confirmation** (run `24899203627` in progress) |
| Phase 5 Mocked gate | 30-min timeout | Chronically cancelled; documented in session memory under ES facet-aggregation race + broader systemic issue |

CI on `4653f3e` is the authoritative notification-lane verification — local `mvnw` is Docker-wrapped and Docker wasn't running on this dev box, so GitHub Actions is the definitive backend gate.

## 2. Priority 0 — Close the Notification Lane (≈0.75 day)

Codex's own `P5_RM_PRESET_DELIVERY_NOTIFICATION_REMAINING_WORK_20260424.md` is explicit:

> Do not start email/webhook work until the current owner-inbox lane is fully closed, otherwise the verification boundary will blur.

Three steps:

1. **Observe CI run `24899251276` / `24899203627`** — the decisive job is the new **RM Preset Delivery Notification Gate**.
2. **If green**: flip the notification-lane closeout doc from `development-complete` to `accepted` (single docs commit).
3. **If red**: diagnose + patch; do not start a new capability until the current lane is green.

This is a **hard prerequisite** — not optional.

## 3. Priority 1 (Next New Capability) — Email Delivery Channel (PR-134 series)

Both the PR-122 closeout and the notification remaining-work doc list email delivery as the top deferred capability. Aligns directly with Phase-1 plan item **#5 Email Outbound SMTP Notifications** (estimated M, 1-2w).

### Rationale
- Reuses the already-shipped activity / preference pipeline — no new data model needed beyond an SMTP config + template store
- Unlocks the webhook channel (shared `NotificationChannel` abstraction)
- Closes one of the three explicitly-deferred channel non-goals

### Slice breakdown (5 additive slices)

| PR | Content | Estimate |
|----|---------|----------|
| PR-134 | Backend: `EmailNotificationService` + `EmailTemplate` entity + Liquibase migration + `spring-boot-starter-mail` dep | 1-2 d |
| PR-135 | `NotificationChannel` abstraction: `InboxChannel` (current) + `EmailChannel` impls; `EcmEventListener` fans out per user preference | 1 d |
| PR-136 | New preference key `notifyBy.email` (default `false`); Schedule dialog + preset card UI toggle | 0.5 d |
| PR-137 | Wire the two `rm.report_preset.delivery.*` activity events into the email channel; mocked + full-stack smoke | 1-2 d |
| PR-138 | CI: isolated **Email Channel Gate** job (mirrors the PR-132 Notification Gate pattern) | 0.5 d |

**Total estimate: 4-6 days.**

## 4. Priority 2 (Can Run in Parallel with Priority 1)

These are **independent** — do not tangle with the email lane's verification boundary.

### A. Phase 5 Mocked Gate Systemic Investigation

- Known flakes: `search-preview-status.spec.ts:235` (ES facet-aggregation race); broader 30-min job timeout
- Memory entries `feedback_es_facet_aggregation_race.md` and `feedback_local_is_not_ci_verification.md` explicitly warn **not to patch with more retry windows** — needs a real fix
- Suggested slice:
  1. Pull the last 3 Phase 5 artifacts
  2. Identify common root cause (likely ES refresh policy or `serve -s build` response-header / caching behaviour)
  3. Decide: fix vs. mark gate as advisory + remove from the required-checks list
- **Estimate: 0.5-1 d for investigation; fix effort depends on findings**

### B. Notification External Routing (after Email lane ships)

Ranked by reuse of the Email lane infrastructure:

| Capability | Effort | Dependency |
|------------|--------|------------|
| Webhook delivery channel | 1-2 d | Requires PR-135's `NotificationChannel` abstraction |
| Per-preset notification preference overrides | 1-2 d | Standalone (extends preference key schema) |
| SLO alerting / escalation policy | ~1 wk | Needs threshold definition + new audit event class |
| Cross-owner / delegated delivery policy | ~1 wk | Needs a new authorization model |

## 5. Medium-Term (2-4 weeks) — Phase-1 Plan Residuals

From `/Users/chouhua/.claude/plans/drifting-tinkering-truffle.md`:

| Item | Size | Notes |
|------|------|-------|
| #3 Scheduled User Actions | S (3-5 d) | Backend largely exists from PR-95..109 scheduler reuse; gap is API surface + minimal UI |
| #7 Site Invitation Workflow | M (1-2 w) | Standalone; token-based accept/reject pattern |
| #1 Smart/Virtual Folders | M (1-2 w) | `Folder.isSmart` field already shipped; gap is runtime query execution via `FacetedSearchService` |
| #2 Legal Holds | L (2-4 w) | Blocks / is blocked by #8 Disposition Schedules |

**Do not start these in parallel with the email lane or the notification close-out.** Each Phase-1 item should be its own lane with its own verification gate.

## 6. Longer-Term (1-3 months) — Phase-2 / Phase-3

Defer selection until the current notification + email lanes are fully closed. Candidates from the master plan:

- #4 Custom Model Management (no-code)
- #6 LDAP/AD Directory Sync
- #8 Full Disposition Schedules (requires #2 Legal Holds)
- #9 Property Encryption at Rest
- #10 Generic OAuth Credential Store

## 7. Explicit Non-Goals for This Plan

- Email-server infrastructure (we use Spring's `JavaMailSender`; ops decision lives outside this repo)
- External alerting destinations (Slack, PagerDuty, etc.) — would be a separate capability after webhooks land
- Multi-tenant isolation of notification streams — current owner-scoping is sufficient until product asks for more
- Backporting the new capabilities to earlier Athena releases

## 8. Recommended Behaviour for the Next `/continue`

| CI State on `4653f3e` | Next Action |
|-----------------------|-------------|
| Notification Gate + Backend Verify both ✅ | Step `2.2` — flip closeout to `accepted`, single docs commit |
| Either job ❌ | Diagnose + patch **before** starting any new capability |
| Still in progress | Wait — do not start email lane (keeps verification boundary clean) |
| Gate green and closeout accepted in a prior turn | Start **PR-134** (email backend foundation) |

## 9. Change-Management Notes

- **`gh auth` drift**: the push on `4653f3e` required `gh auth switch --user zensgit` because the active account had become `rhe91709-netizen`. Watch for this on any future 403; no permanent fix recommended here.
- **Docker availability**: backend tests depend on the Docker-wrapped `mvnw`. Plan any local Java verification steps around Docker being up; otherwise delegate to CI.
- **Phase 5 Mocked job**: will likely keep reporting `cancelled` until section 4.A investigation lands. Do not treat it as a regression signal for unrelated work.

## 10. Summary Table

| Priority | Work | Blocking? | Estimate |
|----------|------|-----------|----------|
| P0 | Close notification lane (observe CI, flip to accepted) | Yes | 0.75 d |
| P1 | Email delivery channel (PR-134..138) | After P0 | 4-6 d |
| P2.A | Phase 5 Mocked investigation | No — independent | 0.5-1 d + fix |
| P2.B | Webhook / per-preset overrides / SLO / delegation | After P1 | 1-7 d each |
| P3 | Phase-1 residuals (#3, #7, #1, #2) | Independent lanes | S-L per item |
| P4 | Phase-2 / Phase-3 capabilities | Deferred | Select later |
