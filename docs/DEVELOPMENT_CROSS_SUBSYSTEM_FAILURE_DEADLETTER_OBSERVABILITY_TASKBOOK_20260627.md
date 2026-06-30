# Cross-Subsystem Failure / Dead-letter Observability — Day-1 Taskbook / Gap Audit

> **Update (2026-06-29):** the first-cut shipped (#48 / verified #49), and the **OCR failed/running**
> signal this doc deferred in §1.6/§2/§7 is now **shipped via #52** as **Option A** — a dedicated,
> indexed `documents.ocr_status` column (not a `nodes.metadata` jsonb index), count-only on the
> Failure Inventory card. The §1.6/§2/§7 "OCR → defer (unindexed)" lines below are a 2026-06-27
> point-in-time view, now superseded for OCR. The remaining decision-gated items **#2 (async control
> plane), #4 (Mail ProcessedMail ERROR), #5 (licensing)** are tracked, with code-grounded options,
> in `DEVELOPMENT_FAILURE_INVENTORY_BACKLOG_2_5_DECISION_TASKBOOK_20260629.md`.

- Date: 2026-06-27
- Status: **Day-1 gap audit + taskbook. NOT YET RATIFIED** — §7 boxes are open; the owner's scope / boundaries / "not-now" list (already given) constrain it, but the formal stamp is the owner's.
- Line: A read-only **cross-subsystem failure / dead-letter inventory** for the *content-processing* failures left after queue-backlog + scheduler-run.
- Owner decision requested: which failure surfaces in the first cut, reuse-vs-recompute for the already-surfaced counts, and the surface (§7).

## 0. Executive finding

There are **two distinct "failure" axes** in Athena, and conflating them is the main trap this line must avoid:

- **Axis A — async export/job task failures.** Already **cross-subsystem aggregated** by the `asynctask` governance layer
  (6 domains, an overview with `failedCount` / `timedOutCount` / `expiredCount`, an AdminDashboard "Async Task Health"
  panel). **This line must NOT re-aggregate it.**
- **Axis B — underlying content-processing failures / dead-letters.** Scattered and uneven: preview has a mature
  dead-letter surface; transfer has an index-backed FAILED count (but no failure list); mail has its own PII-bearing
  ERROR diagnostics surface over an **unindexed** table; OCR failures live in unindexed `Document.metadata`. This line
  is about **Axis B**.

The honest conclusion is that **the gap is narrower than the title suggests**. Much of Axis B's *cheap* signal is
already surfaced (transfer FAILED count and mail account-level fetch-health are both already on the queue-backlog card),
and the expensive parts (OCR, ProcessedMail ERROR scans) are unindexed and/or PII-laden. The one genuinely
**un-surfaced cheap signal** is the **preview dead-letter count** (absent from queue-backlog *and* from the async
`failedCount`). So the real first-cut value is: **preview dead-letter count on the dashboard + a "failure-triage" hub
that links the existing deep surfaces** — i.e. *co-location and navigation*, not a pile of new data. Day-1 records this
honestly rather than inflating the gap.

## 1. Current code facts (grounded)

### 1.1 The two axes, and the same-word-different-object overlap (read this first)
"preview" and "ops" appear in **both** axes but refer to **different objects with zero numeric overlap**:
- **Axis A "preview"** = `PreviewDiagnosticsController.summarizeRenditionResourcesCsvAsyncExportTaskSnapshot` — the
  *rendition-resources CSV **export task*** lifecycle. **Axis B "preview"** = `PreviewDeadLetterRegistry` — *rendering
  dead-letter* entries. Different counters.
- **Axis A "ops"** = `OpsRecoveryController.summarizeHistoryExportAsyncTaskSnapshot` — the *history **export** task*.
  The dead-letter **replay/clear** actions in the same `OpsRecoveryController` act on the preview registry and are
  **not** in any `failedCount`.
This must be stated up front; otherwise a reader sees "preview"/"ops" twice and assumes double-counting.

### 1.2 Preview dead-letter — the mature reference (do NOT rebuild)
- `PreviewDeadLetterRegistry` (`com.ecm.core.preview`): terminal preview failures keyed by `documentId|renditionKey`;
  dual backend (in-memory + Redis ZSET `ecm:preview:deadletter:index`). Entry fields: `entryKey, documentId,
  renditionKey, reason, category, policyKey, sourceStage, failedAt, attempts, occurrences, lastReplayAt, replayCount`.
  `maxEntries` default **5000**, TTL **7 days**. Populated only by `PreviewQueueService.markDeadLetter(...)` at three
  terminal stages. **Cheap count** available (`list(int)` + the DTO's `itemCount`).
- Surfaced read: `PreviewDiagnosticsController` `@RequestMapping("/api/v1/preview/diagnostics")` `@PreAuthorize hasRole('ADMIN')`
  → `GET /dead-letter`, `GET /dead-letter/export` (CSV); plus `POST /dead-letter/replay-batch` / `/clear-batch`.
- Recovery control over the **same** registry: `OpsRecoveryController` `@RequestMapping("/api/v1/ops/recovery")` (ADMIN)
  — queue-by-reason/window, replay/clear-batch, replay/clear-by-filter, dry-run, history.
- Frontend: `PreviewDiagnosticsPage` at `/admin/preview-diagnostics` (summary chips + items table + by-reason grouping).

### 1.3 Async-task governance — Axis A (already cross-subsystem; do NOT re-aggregate)
- `com.ecm.core.asynctask`; **6** `AsyncTaskGovernanceProvider` beans in `AsyncTaskGovernanceConfiguration`:
  `audit, ops, search, preview, batchDownload, propertyEncryption` (confirmed: `SimpleAsyncTaskGovernanceProvider` is
  the only impl, that config the only registration site).
- `AsyncTaskGovernanceService.buildOverview()` → `AnalyticsController` `GET /api/v1/analytics/async-governance/overview`
  (+ `/tasks`), class-level `@PreAuthorize hasRole('ADMIN')`. DTO carries `failedCount` (strictly FAILED) plus separate
  `timedOutCount` / `expiredCount` (the latter two contributed **only** by `ops` + `preview`).
- AdminDashboard "Async Task Health Overview" + "Recent Async Tasks" panels. The governance action contract exposes
  only **cancel / download / cleanup / acknowledge** — **no retry** at the cross-domain layer (per-domain retry/replay
  exists in `OpsRecoveryController` / `PreviewDiagnosticsController` / `SearchController`, but that is control-plane).

### 1.4 Transfer failures
- `ReplicationJob` (`com.ecm.core.entity`): status enum `PENDING|RUNNING|COMPLETED|FAILED|CANCELED`. Failure fields:
  `lastMessage` (TEXT), `transportStatus` (enum), `transportMessage` (TEXT, raw `ex.getMessage()`), `errorLog` (TEXT,
  raw), `entryReport` (jsonb, per-entry incl. FAILED), `reportTruncated`. **No** `errorMessage`/`failureReason`/`errorType`.
- `idx_replication_job_status` exists (Liquibase `060`, never dropped) → **`countByStatus(FAILED)` is cheap/index-backed**
  and is **already computed** in `QueueBacklogObservabilityService.transferBacklog()` (the queue-backlog card's `failedCount`).
- `TransferReplicationController` `/api/v1` (ADMIN): `GET /replication/jobs` (paginated **all** statuses, no filter),
  `GET /replication/jobs/{id}`, `POST /replication/jobs/{id}/retry`. **No FAILED-summary endpoint; `ReplicationJobRepository`
  has `countByStatus` but no `findByStatus` page method.**
- ⚠️ Anti-pattern to NOT replicate: `RecordsManagementController` `GET /api/v1/records/operations` computes its failed
  governed-transfer count via `replicationJobRepository.findAll().stream().filter(...)` — a **full-table in-memory scan**.

### 1.5 Mail failures (+ PII)
- `ProcessedMail` (`mail_processed_messages`): `status` (`PROCESSED|ERROR`), `errorMessage` (TEXT), `subject` (real
  subject), `processedAt`, `receivedAt`, `accountId`. **PII:** `subject` is the real email subject; `errorMessage` stores
  **raw `e.getMessage()` UNTRUNCATED** (`recordProcessedMail` lines 1062-1063, spot-checked) — can embed addresses /
  filenames. (Contrast: `MailAccount.lastFetchError` is `truncateError(...)` to 500; runtime `topErrors` summarized to 240.)
- **No index on `status` or `processed_at`** (only `idx_mail_processed_account` + unique `(account,folder,uid)`,
  Liquibase `014`; spot-checked) → every ERROR aggregate/list is a **full scan**. Retention: `MailProcessedRetentionService`
  `@Scheduled` 02:00 daily deletes by the **unindexed** `processed_at` (default 90d).
- Already-exposed (so do NOT rebuild): `GET /api/v1/integration/mail/diagnostics` (filterable ERROR inventory **with PII**),
  `/diagnostics/export` (CSV), `/runtime-metrics` (`topErrors`). **Cheap, non-PII signal:** account-level
  `MailAccount.lastFetchStatus` (`"ERROR"`) + `lastFetchAt` — **already computed** in `QueueBacklogObservabilityService`
  mail fetch-health.
- Precedent for the PII guard: `MailFetcherService` already **deliberately avoids emitting `e.getMessage()`** in the
  OAuth/connect paths ("Phase 2 logging audit … embeds PII", lines 169/179/188) — the same posture this line inherits.

### 1.6 OCR failures (unindexed → deferred)
- **No** OCR dead-letter. Failures live only in `Document.metadata` (`nodes.metadata` jsonb): `ocrStatus="FAILED"`,
  `ocrFailureReason`. The only index on that column is GIN `idx_node_metadata` with **default `jsonb_ops`** (serves
  containment, **not** `metadata->>'ocrStatus' = 'FAILED'`); **no btree expression index exists**, and no changelog
  references OCR. (A JSON **containment**-form rewrite — `metadata @> '{…}'` — could use that GIN, but selectivity across
  all nodes is unproven and the 'running' / oldest-failure forms still don't fit, which is exactly the deferred
  `Document.metadata` index-strategy decision in §8.) So failed/running OCR is **unindexed / expensive** — the authors already defer it
  (`OcrQueueService.getBacklogSnapshot()` Javadoc; only the Redis **depth** is cheap and already on the queue-backlog card).
- No OCR failure endpoint or admin UI exists.

## 2. Inventory + entry-points + "safe-to-aggregate" verdict (answers the three Day-1 questions)

"Safe to aggregate" = **cheap** (index-backed or O(1)) **∧ non-PII** (count/time/type only) **∧ non-duplicative**
(not already on the queue-backlog card or in the async `failedCount`).

| Subsystem | Cheap failure signal | Indexed/O(1)? | Already has an entry point | PII risk | Safe to aggregate now? |
|---|---|---|---|---|---|
| **Preview dead-letter** | dead-letter `itemCount` (+ category tally, latest `failedAt`) | ✅ O(1) (`list`/`itemCount`) | `/admin/preview-diagnostics` (deep) | low (count/category only; reasons sanitized) | ✅ **yes — the anchor** (new: not on dashboard, not in async `failedCount`) |
| **Transfer** | `countByStatus(FAILED)` | ✅ `idx_replication_job_status` | `TransferReplicationPage` + queue-backlog card | raw text in `transportMessage`/`errorLog` (count is safe) | ✅ count only — but **duplicative** (already on queue-backlog) → §7 reuse decision |
| **Mail (account-level)** | accounts with `lastFetchStatus="ERROR"` | ✅ account rows | mail diagnostics + queue-backlog fetch-health | `username`=email (count is safe) | ✅ count only — but **duplicative** (already on queue-backlog) → §7 reuse decision |
| **Mail (ProcessedMail ERROR)** | `count WHERE status='ERROR'` | ⛔ **unindexed** | `/integration/mail/diagnostics` (PII-gated) | **high** (`subject`, raw `errorMessage`) | ⛔ **exclude** — link out to the existing deep surface |
| **OCR failed/running** | `metadata->>'ocrStatus'='FAILED'` | ⛔ **unindexed** | none | low | ⛔ **defer** — note the invisibility |

There ARE failure surfaces (preview / transfer / mail / OCR); most already HAVE entry points (preview diagnostics, mail
diagnostics, transfer jobs, queue-backlog); only **preview dead-letter count** and **OCR** lack a dashboard presence,
and only the **cheap, non-PII, non-duplicative** signals are safe to aggregate.

## 3. The gap (honest, narrow)

There is no single read-only place that answers "across subsystems, what is **terminally failing** right now?" — but the
practical gap is small: (a) **preview dead-letter has no dashboard presence** (only its own deep page); (b) there is
**no failure-triage hub** linking the existing deep surfaces (preview diagnostics / mail diagnostics / transfer jobs);
(c) **OCR failures are entirely invisible** but are unindexed (defer). The transfer FAILED count and mail account-level
ERROR are *already* visible on the queue-backlog card, so a cross-subsystem inventory mostly **co-locates and links**,
adding genuinely new data only for preview dead-letter.

## 4. Recommended first-cut slice (cheap, honest, read-only) — for ratification, not yet built

Anchor on the one new cheap signal + linkage; keep it small (do not pad to disguise the thinness):
- **New data:** surface the **preview dead-letter count** (+ category tally + latest `failedAt`) on the dashboard via a
  cheap registry read (`itemCount` / `list`).
- **Failure-triage hub:** a read-only card co-locating the cheap counts and **linking out** to each deep ADMIN-gated
  surface (preview diagnostics, mail diagnostics, transfer jobs) for detail — mirroring the queue-backlog / scheduler-run
  card style, per-source `available` flag.
- **Transfer + mail:** count only. Because both are **already computed** in `QueueBacklogObservabilityService`,
  **reuse-vs-recompute is a §7 ratification item** — do not silently duplicate that logic. **No transfer FAILED list**
  (no endpoint today; it drags in `transportMessage`/`errorLog`).
- **Exclude / defer:** ProcessedMail ERROR scan (unindexed + PII → link to `/integration/mail/diagnostics`); OCR
  failed/running (unindexed → deferred, note invisibility).

## 5. Boundaries / out of scope

- **Read-only inventory — NO replay / clear / retry / requeue / cancel.** Those already exist per-domain
  (`OpsRecoveryController`, `PreviewDiagnosticsController`, `SearchController`) and are a **control plane**, explicitly excluded.
- **No unified control plane, no action buttons.**
- **No unindexed large-table scans** — excludes ProcessedMail `status` scans and OCR `Document.metadata` scans; and must
  not adopt the `RecordsManagementController` `findAll().stream()` pattern.
- **Do NOT re-aggregate the 6 async-task governance domains** (Axis A) — a cross-subsystem inventory of Axis B only.
- **Don't touch the closed lines** (queue-backlog, scheduler-run, storage, OAuth, logging) or the governance domains.
- Day-1 answers "what failure surfaces exist, which already have entry points, which can be safely aggregated" — **not** the slice design.
- Observed, out of scope: `BatchDownloadController` carries no `@PreAuthorize` (Axis A, pre-existing) — note only, do not fix here.

## 5A. Required Day-2 guards (owner) — index-first AND PII-safe (both, symmetric across subsystems)

- **Index-first (no unbounded scan).** Every aggregated signal must be index-backed or O(1): transfer via
  `idx_replication_job_status`, preview via the registry (Redis ZCARD / in-memory size), mail via account rows. A missing
  index → that signal stays **deferred** (OCR, ProcessedMail ERROR), **not** an unindexed scan.
- **PII-safe aggregation — symmetric for ALL subsystems** (anchored to the existing sensitive-logging governance line, not
  a fresh framing). The aggregate surface emits **count + timestamp + type/category only**; **raw failure text never
  leaves the existing ADMIN-gated deep surface**. This applies equally to mail (`subject`, raw `errorMessage`) **and
  transfer** (`transportMessage`, `errorLog`, `lastMessage`, `entryReport`) — both are the same raw-error-text class. The
  card **links out**; it does not re-emit raw text. (The guard does not depend on whether mail's `errorMessage` is
  truncated — real subjects and even truncated error text are still PII.)

## 6. Tests (Day-2, when ratified)
- Service: aggregates the chosen cheap signals; an unavailable source → `available=false` + null, never throws; the DTO
  carries **no raw failure text** (assert no `subject` / `errorMessage` / `transportMessage` / `errorLog` field).
- Index/PII check (§5A): the aggregated reads are index-backed/O(1) (no ProcessedMail `status` scan, no `Document.metadata`
  scan); a PII assertion that the aggregate DTO exposes only count/time/type.
- Controller: admin-only (`*SecurityTest`, CLAUDE.md convention) + shape.
- Frontend: the card renders the sub-panels + links; a per-source failure is isolated (mirror queue-backlog).

## 7. Ratification checklist — OPEN (recommendation; owner to ratify)
- [ ] **First-cut surfaces** → recommend **preview dead-letter count (new)** + a failure-triage hub linking the existing deep surfaces.
- [ ] **Transfer / mail** → **count only**, and decide **reuse `QueueBacklogObservabilityService`** vs recompute (recommend reuse; no duplication).
- [ ] **ProcessedMail ERROR** → **exclude** (unindexed + PII); link to `/integration/mail/diagnostics`.
- [ ] **OCR failed/running** → **defer** (unindexed `Document.metadata`); note invisibility.
- [ ] **Surface** → AdminDashboard card (inherit queue-backlog/scheduler-run) vs extend an existing page.
- [ ] **Guards** → §5A index-first **and** PII-safe (count/time/type only; raw text stays in the deep surface).

## 8. Out of scope / NOT now (owner's list, carried verbatim)
- **Async control plane** — heavier product semantics, easily slides into action buttons.
- **OCR failed/running index** — too narrow, and needs a `Document.metadata` index-strategy decision first.
- **Mail error backlog** — needs a `ProcessedMail` index + retention + PII-display decision first.
- **Real licensing** — a commercial product line, not a small closed loop.

## 9. Recommendation

Ratify a **deliberately narrow** first cut: surface the **preview dead-letter count** (the one new cheap signal) plus a
read-only **failure-triage hub** that co-locates the already-cheap transfer / mail counts (reusing
`QueueBacklogObservabilityService`) and **links out** to the existing ADMIN-gated deep surfaces — under both §5A guards
(index-first **and** PII-safe). **Defer** OCR and ProcessedMail-ERROR scans (unindexed / PII), and keep **all** recovery
actions out (read-only). Writing the gap honestly-narrow is the correct Day-1 outcome: the value here is co-location and
navigation, with genuinely new data only for preview dead-letter.
