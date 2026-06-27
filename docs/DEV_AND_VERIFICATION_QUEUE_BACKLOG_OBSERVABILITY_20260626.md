# Queue Backlog Observability — Development & Verification

- Date: 2026-06-26
- Line: Athena read-only "Queue Backlog" admin surface for **OCR / mail / transfer** (the #40 Day-1 candidate #2).
- State: **COMPLETE on `main`.** taskbook → ratify (§7 + the §5A index-first guard) → build → CI 7/7.
- Repos: Athena only. **Observability-only (NOT a control plane); no requeue / cancel / retry / schedule change.**

## 1. State at a glance

After scheduler-run, the three subsystems that still lacked a backlog surface — OCR, mail, transfer — now report a
read-only "Queue Backlog" snapshot on an AdminDashboard card. An admin can answer **"is OCR backing up? is mail fetch
failing or long-since-success? is transfer replication piling up PENDING / stuck RUNNING / FAILED?"** without
paginating lists, SSH, or DB queries. Cheap signals only — index-backed DB reads / O(1) Redis / account-level
fetch-health; the expensive OCR failed/running scan stays deferred. Preview already had its own backlog surface and is
untouched.

| PR | What | Merge SHA |
|---|---|---|
| #43 | Day-1 taskbook — gap audit + ratified §7 + the §5A index-first guard | `c61674e` |
| #44 | Implementation — service + DTO + endpoint + OCR snapshot + repo methods + card + tests | `1b7350e` |

Local `main` == `origin/main` == `1b7350e` at closeout.

## 2. The gap (recap)

Preview was the only subsystem with a backlog surface (`PreviewDiagnosticsController` + `RedisScheduledQueueStore`,
depth + dead-letter). The other three are **heterogeneous** — no shared queue abstraction — and uneven in
backlog-readability: transfer is a clean DB status enum, OCR has a Redis depth but an unindexed `Document.metadata`
scan for failed/running, and mail is **not a queue at all** (IMAP is pull-based — "pending" lives in the remote
inbox). So the honest first cut surfaces the **cheap, already-modellable signals only**, per the backlog-readability
table in the taskbook.

## 3. What was built (code-grounded)

- **`QueueBacklogSummaryDto`** (record) — `ocr / mail / transfer`, each a nested record with an `available` flag for honesty:
  - `OcrBacklog(available, pendingDepth, oldestPendingAgeSeconds?)`
  - `MailBacklog(available, lastSuccessAt?, errorRate, errors, status)`
  - `TransferBacklog(available, pendingCount, runningCount, failedCount, oldestPendingAgeSeconds?, stuckRunningCount, stuckThresholdMinutes)`
- **`QueueBacklogObservabilityService`** — aggregates the three; **each source is wrapped in its own try/catch** → a
  failure yields `available=false` (zeros/nulls) instead of throwing, so one bad source never sinks the card.
  - **OCR** — `OcrQueueService.getBacklogSnapshot()`: Redis `scheduledCount()` (ZCARD, O(1)) + `peek(1)` for the oldest
    item's age past its scheduled time; the in-memory fallback returns the queued-jobs size (no oldest age). A Redis
    failure → `available=false`.
  - **Mail** — account-level fetch-health: `MailAccountRepository.findAll()` filtered to accounts whose `lastFetchAt`
    falls within a **hardcoded 60-minute window**; it counts attempts / successes / errors → `errorRate`, the newest
    `lastSuccessAt`, and a status of **`HEALTHY`** (no errors) / **`DEGRADED`** (mixed) / **`DOWN`** (no successes) /
    **`UNKNOWN`** (no attempts in window). It reads only `MailAccount.lastFetchAt` / `lastFetchStatus` — **never**
    `mail_processed_messages` (no unindexed `ProcessedMail.status` scan).
  - **Transfer** — index-backed `ReplicationJobRepository`: `countByStatus(PENDING|RUNNING|FAILED)`, oldest-pending age
    via `findFirstByStatusOrderByCreatedAtAsc(PENDING)`, stuck via `countByStatusAndStartedAtBefore(RUNNING, now - T)`,
    where `T = ecm.queue-backlog.transfer-stuck-minutes` (`@Value`, default 60). The stuck threshold is **display-only**
    and is **the one configurable knob** (the mail window is not configurable).
- **`ReplicationJobRepository`** — 3 new read-only derived queries (above); no `@Query`, no native SQL.
- **`OcrQueueService`** — additive `getBacklogSnapshot()` + an `OcrBacklogSnapshot` record; the existing enqueue path is
  unchanged, and OCR failed/running is deliberately **not** added (it would need an unindexed `Document.metadata` scan).
- **`QueueBacklogAdminController`** — `GET /api/admin/queue-backlog` (+ `/api/v1/...`), `@PreAuthorize hasRole('ADMIN')`,
  returns the DTO. **Observability-only — no control endpoints.**
- **Frontend `QueueBacklogCard`** on `AdminDashboard` — three sub-panels (OCR / Mail fetch / Transfer replication) with a
  mail status chip; **failure-isolated** fetch — a per-subsystem `available=false` shows "unavailable" without breaking
  the others, and a whole-fetch error shows a warning and renders no panels rather than crashing the dashboard.

## 4. The §5A index-first guard + §7 ratification — honored (verified, not asserted)

- **§5A (index-first, no unbounded scan) — verified against the schema, not just the entity annotation.** Athena's
  schema is **Liquibase-managed** with Hibernate `ddl-auto: validate` (`application.yml` / `application-prod.yml`), so
  `@Index` on `@Table` is documentation — the authoritative DDL is the migration. Liquibase changeset
  `060-create-transfer-replication-tables.xml` creates `idx_replication_job_status` (column `status`, line 101) and
  `idx_replication_job_created_at` (column `created_at`, line 104). Therefore `countByStatus` and
  `findFirstByStatusOrderByCreatedAtAsc` are index-backed; the stuck query narrows the row set by the `status` index
  (RUNNING is a small set) before comparing `started_at`, so it is bounded — not a full-table scan. OCR is O(1) Redis;
  mail reads `mail_accounts` rows only. **No new migration was needed and none was added** — the indexes already existed.
- **§5A was enforced in review, not only at design time.** The pre-merge #44 initially sourced mail via
  `MailFetcherService.getRuntimeMetrics()`, which aggregates `mail_processed_messages` (top-errors/trend) — an
  unindexed `ProcessedMail` scan, i.e. a §5A violation. Gate review caught it; #44 was corrected to the account-level
  `MailAccountRepository.findAll()` read in §3, and #43's taskbook was reworded off the runtime-metrics / `ProcessedMail`
  framing — both **before merge**, so the landed `1b7350e` / `c61674e` already reflect the account-level read (targeted
  backend re-run 6/0; CI 7/7 after the correction).
- **§7 ratification:** transfer (full) + OCR (depth/oldest) + mail (fetch-health) — all three ✅; OCR failed/running
  **deferred** ✅; mail framed as **fetch-health** (account-level) ✅; stuck = **display-only + one configurable
  threshold** (no OK/WARN/CRITICAL, no alerting) ✅; surface = **AdminDashboard "Queue Backlog" card** (no new page, no
  control plane) ✅.

## 5. Verification

**CI — #44 7/7 green** (run `28255519448`, push to `main`): Backend Verify, Frontend Build & Test, Phase C Security
Verification, Acceptance Smoke (3 admin pages), Phase 5 Mocked Regression Gate, Property Encryption Closeout Gate,
Frontend E2E Core Gate. #43 (taskbook) 7/7 green (run `28255466709`).

**Tests (additive, in #44 — 6 backend + 3 frontend):**
- `QueueBacklogObservabilityServiceTest` (2) — (1) aggregates all three: OCR depth/oldest; mail `errors` / `errorRate`
  = 2/3 / `DEGRADED` / newest `lastSuccessAt`; transfer pending/running/failed/stuck/`stuckThresholdMinutes` and an
  oldest-pending age ~30m; (2) a failing source per subsystem → `available=false`, the service **never throws**, and
  `stuckThresholdMinutes` still surfaces even when the transfer source is down.
- `QueueBacklogAdminControllerSecurityTest` (3) — unauthenticated → 401, `ROLE_USER` → 403, `ROLE_ADMIN` → 200 (the
  CLAUDE.md "new controller ships a `*SecurityTest`" convention; `@WebMvcTest` + a `TestSecurityConfig` mirroring production).
- `QueueBacklogAdminControllerTest` (1) — `standaloneSetup`, asserts the JSON shape across all three subsystems.
- `QueueBacklogCard.test.tsx` (3) — renders the three panels; a per-subsystem `available=false` shows "unavailable"
  without breaking the others; a fetch rejection shows a warning and renders no panels (no crash).

**Local targeted run (pre-push, recorded in the #44 commit):**
```bash
cd ecm-core        && ./mvnw '-Dtest=QueueBacklog*' test             # 6 tests / 0 failures, BUILD SUCCESS
cd ../ecm-frontend && npm test -- --watchAll=false QueueBacklogCard  # 3/3 (on the prior head)
```
Per CLAUDE.md, `./mvnw` requires Docker on some dev boxes, so the **authoritative gate is CI** — 7/7 above.

**Manual smoke (if a stack is up):** `GET /api/admin/queue-backlog` as ADMIN → `ocr/mail/transfer` each with
`available`; transfer numeric `pendingCount / runningCount / failedCount / oldestPendingAgeSeconds / stuckRunningCount`
+ `stuckThresholdMinutes`; mail `status ∈ {HEALTHY, DEGRADED, DOWN, UNKNOWN}`. As `ROLE_USER` → 403; unauthenticated → 401.

## 6. Boundaries / out of scope (deliberate)

- **Read-only — NO control plane** (no requeue / cancel / retry / trigger); no retry-strategy or schedule change.
- **No expensive scans** — OCR failed/running (an unindexed `Document.metadata` scan) is **deferred**; mail does **not**
  add an unindexed `mail_processed_messages` / `ProcessedMail.status` aggregation; no IMAP login to count remote-inbox pending.
- **Mail is fetch-health, not queue-depth** (honest — pull-based IMAP has no local depth); its window is a fixed 60m,
  and only the transfer stuck threshold is configurable.
- **No alerting / risk-escalation** — the stuck threshold *displays*, it does not push alerts; no OK/WARN/CRITICAL state machine.
- **Reuse, don't rebuild** — preview's own backlog surface (`PreviewDiagnostics`) is untouched; this mirrors the
  scheduler-run card style. The closed lines (scheduler-run, storage, OAuth, logging) and the governance domains are untouched.
- **Re-entry** would each be a *new* taskbook: OCR failed/running (needs a `Document` index decision), a `ProcessedMail`
  error-backlog signal (needs an index/migration first), a control plane (requeue/cancel/trigger), cross-subsystem
  dead-letter aggregation, or threshold alerting.

## 7. Conclusion

Queue Backlog observability is complete and verified on `main` (taskbook `c61674e` → implementation `1b7350e`).
Operators see, read-only, OCR depth/oldest, mail fetch-health, and transfer pending/running/failed/oldest/stuck on one
AdminDashboard card — closing the largest ops blind spot remaining after scheduler-run — with index-backed DB reads
(verified against Liquibase changeset 060, not just the entity annotation), O(1) Redis, and account-level mail reads,
each source failure-isolated, all CI-proven 7/7 against the ratified §7 + §5A guards. The expensive OCR scan, a mail
error-backlog, a control plane, and threshold alerting remain separate, deliberate decisions.
