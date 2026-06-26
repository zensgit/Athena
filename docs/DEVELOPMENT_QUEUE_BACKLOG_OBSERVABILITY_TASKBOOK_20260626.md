# Queue Backlog Observability — Day-1 Taskbook / Gap Audit

- Date: 2026-06-26
- Status: **Day-1 gap audit + taskbook. RATIFIED (owner, 2026-06-26): full §7 — transfer (full) + OCR (depth/oldest) + mail (fetch-health); OCR failed/running deferred; stuck = display-only + configurable threshold; AdminDashboard card. One added Day-2 guard: index-first reads (§5A). No code until #43 merges.**
- Line: Athena read-only "Queue Backlog" admin surface for **OCR / mail / transfer** (the #40 Day-1 candidate #2).
- Owner decision requested: which queues in the first cut + the mail framing + the stuck semantics + the surface (§7).

## 0. Executive finding

The three subsystems are **heterogeneous and uneven in backlog-readability** — there is no shared queue abstraction,
and **preview already has the reference pattern** (`PreviewDiagnosticsController` + `RedisScheduledQueueStore`, depth +
dead-letter). So this line is a read-only "Queue Backlog" card for the *other three*, and the honest first cut is the
**cheap, already-modellable signals only**:
- **Transfer** — cleanest: `ReplicationJob` + status enum → `countByStatus` / oldest / stuck, all cheap (indexed).
- **OCR** — depth + oldest are cheap (Redis, same store as preview); **failed/running are NOT** (a `Document.metadata`
  scan, no index) → defer.
- **Mail** — **not a queue**: IMAP fetch is pull-based, "pending" lives in the remote inbox (not tracked locally), so
  there is **no queue depth**. Its backlog signal is account-level *fetch health* (last-success age + recent
  success/error rate), matching the visible semantics of `/mail/runtime-metrics` for the fields this card needs.

## 1. Current code facts (grounded)

### 1.1 Preview — the reference pattern (already covered; do NOT rebuild)
`RedisScheduledQueueStore` (Redis ZSET `ecm:queue:preview:schedule`) → `scheduledCount()` (ZCARD) + `peek()` (oldest);
`PreviewDeadLetterRegistry` (failed); surfaced at `GET /api/v1/admin/preview/queue/summary` + `PreviewDiagnosticsPage`.
**The new card mirrors this read-only style; preview itself stays as-is.**

### 1.2 OCR — `OcrQueueService` (Redis, same store as preview)
- Store: `RedisScheduledQueueStore` (`ecm:queue:ocr:schedule`) + in-memory fallback; status in `Document.metadata['ocrStatus']`.
- **Cheap:** `scheduledCount()` (depth, O(1) ZCARD), `peek(1)` (oldest-pending age).
- **Expensive / missing:** failed & running counts = a `Document.metadata['ocrStatus']` aggregation — **no index, table scan**.
- Existing surface: only `POST .../ocr/queue` (enqueue one). No depth endpoint, no metric, no UI.

### 1.3 Mail — `MailFetcherService` + `ProcessedMail` (pull-based, NOT a queue)
- IMAP polling (scheduled); `ProcessedMail` (`mail_processed_messages`, status `PROCESSED|ERROR`) logs **completed**
  messages only — there is **no local "pending fetch"** (it is in the remote inbox).
- **Cheap account-level source:** `MailAccount.lastFetchAt` + `lastFetchStatus` for recent success/error rate and
  last-success age. Do **not** introduce a queue-depth-style `ProcessedMail.status` count unless an index/migration is
  explicitly added in a separate decision.
- **Already exposed:** `GET /api/v1/mail/runtime-metrics` (`errorRate`, `lastSuccessAt`, `lastErrorAt`, `status`,
  `avgDurationMs`, `topErrors`), `GET /api/v1/mail/fetch/summary` (last-run stats). UI `MailAutomationPage` (no backlog card).

### 1.4 Transfer — `ReplicationJob` + `ReplicationJobStatus` (DB, cleanest)
- Entity `replication_jobs`; `status` (PENDING/RUNNING/COMPLETED/FAILED/CANCELED, indexed); `createdAt`, `scheduledFor`,
  `startedAt`, `completedAt`.
- **Cheap (if repo methods added):** `countByStatus(PENDING|RUNNING|FAILED)`, oldest-pending (`min(createdAt)` where
  PENDING), stuck (`RUNNING` with `startedAt < now - threshold`).
- Existing surface: `GET /api/v1/replication/jobs` (paginated list — **no summary**); UI `TransferReplicationPage` (list only).

## 2. Backlog-readability table

| Signal | OCR | Mail | Transfer |
|---|---|---|---|
| depth / pending | ✅ Redis `scheduledCount()` O(1) | ⛔ N/A (pull-based, no local pending) | ✅ `countByStatus(PENDING)` (idx) |
| oldest-pending age | ✅ Redis `peek(1)` | — (use last-success age instead) | ✅ `min(createdAt)` where PENDING |
| failed count | ⚠️ `Document.metadata` scan (no idx) | ✅ account-level recent ERROR count (`mail_accounts`) | ✅ `countByStatus(FAILED)` (idx) |
| running count | ⚠️ `Document.metadata` scan (no idx) | — | ✅ `countByStatus(RUNNING)` (idx) |
| stuck | ⚠️ (depends on the scan) | — (use last-success age + error rate) | ✅ `RUNNING & startedAt < now-T` |
| last-success / error-rate | — | ✅ account-level `lastFetchAt` / `lastFetchStatus` (same user-facing semantics as runtime metrics subset) | — |

✅ cheap & ready-ish · ⚠️ expensive (defer) · ⛔ not applicable

## 3. The gap
No unified read-only "Queue Backlog" view: an admin cannot answer "is OCR backing up? how stuck? is mail fetch failing
or long-since-success? is transfer replication piling up PENDING / stuck RUNNING / FAILED?" without paginating lists,
SSH, or DB queries. Preview is the only subsystem with a backlog surface.

## 4. Recommended first-cut slice (cheap, honest, read-only)
A `QueueBacklogObservabilityService` aggregating the **cheap** signals from the three sources + a read-only admin
endpoint + an AdminDashboard "Queue Backlog" card (mirror the scheduler-run card + the preview read-only style).

DTO (cheap signals only; per-subsystem `available` flag for honesty):
```
QueueBacklogSummaryDto {
  ocr:      { available, pendingDepth, oldestPendingAgeSeconds? }                 // Redis; null+available=false if Redis off
  mail:     { available, recentErrorCount, lastSuccessAt?, errorRate, status }    // MailAccount last-fetch health
  transfer: { available, pendingCount, runningCount, failedCount, oldestPendingAgeSeconds?, stuckRunningCount }  // ReplicationJob
}
```
Day-2 adds only **read-only repository methods** for transfer + the service + the controller + the card — **no**
business-logic / retry / schedule change. Mail uses account-level last-fetch state; it does not add unindexed
`mail_processed_messages` aggregations for this card.

## 5. Boundaries / out of scope
- **Read-only — NO control** (no requeue / cancel / retry / trigger; that is a control plane, separate).
- **No retry-strategy or schedule change**; capture is pure reads.
- **No expensive scans in the first cut** — OCR failed/running via a `Document.metadata` scan is **deferred** (needs a
  `Document` index decision = a separate slice).
- **No IMAP login** to count remote-inbox "pending" (not feasible / expensive; mail has no local queue).
- **Mail is framed as fetch-health, not queue-depth** (honest — pull-based has no depth).
- **Reuse**: mirror the preview read-only style + the scheduler-run AdminDashboard card; **don't rebuild**
  PreviewDiagnostics (preview already covered).
- **No alerting / notification / risk-escalation system** (thresholds may *display*, but pushing alerts is a separate line).
- Don't touch the closed lines (scheduler-run, storage, logging, OAuth) or the 6 governance domains.

## 5A. Required Day-2 guard (owner — index-first, no unbounded scan)

Every new backlog repo method must be **backed by an index**; a missing index → a small index migration is in-scope,
an **unbounded table scan is NOT allowed**.
- **Transfer** — `ReplicationJob.status` + `created_at` are **already indexed** → `countByStatus` / oldest-pending /
  stuck are index-backed; no migration needed (confirm at implementation).
- **Mail** — `ProcessedMail.status` has **no explicit JPA index** today, and `getRuntimeMetrics()` includes
  `mail_processed_messages` aggregations for top-errors/trend. Day-2 should use the account-level last-fetch subset
  (`MailAccount.lastFetchAt` / `lastFetchStatus`) for this card. If a future slice needs `ProcessedMail` error
  backlog/oldest-error, add an explicit index/migration first. **No unindexed `countByStatus` or aggregate scan on
  `mail_processed_messages` for this card.**
- **OCR** — depth/oldest are O(1) Redis ops (no DB scan); the deferred failed/running stay deferred precisely because
  they would require an unindexed `Document.metadata` scan.

## 6. Tests (Day-2)
- Repo methods: `countByStatus` / oldest-by-status return correct counts/timestamps (transfer); mail account-level
  last-fetch status aggregation returns recent errors/error-rate/last-success without touching `mail_processed_messages`.
- Service: aggregates the three; an unavailable source (Redis off) → `available=false` + null, never throws.
- Controller: admin-only (`*SecurityTest`, CLAUDE.md convention) + shape.
- Frontend: the card renders the three sub-panels + a status; a fetch failure is isolated (mirror the scheduler-run card).
- Index check (§5A): `ReplicationJob.status` / `created_at` already indexed; `ProcessedMail.status` is **not** — Day-2
  must use account-level mail fetch-health, or add an index before any `countByStatus(ERROR)` / oldest-error repo method.

## 7. Ratification checklist — RATIFIED (owner, 2026-06-26)
- [x] **First-cut queues** → **transfer (full) + OCR (depth/oldest) + mail (fetch-health)** — all three.
- [x] **OCR failed/running** → **deferred** (an unindexed `Document.metadata` scan; the index is a separate slice).
- [x] **Mail framing** → **fetch-health** (account-level `lastFetchAt` / `lastFetchStatus`; same status semantics as runtime metrics subset; honest IMAP-pull path).
- [x] **Stuck semantics** → **display-only + one configurable threshold** ("oldest pending Xh / RUNNING>Yh = N"); no OK/WARN/CRITICAL, no alerting.
- [x] **Surface** → **AdminDashboard "Queue Backlog" card** (inherit scheduler-run); no new page, no control plane.
- [x] **Added guard (owner):** index-first reads, no unbounded scan — see §5A.

## 8. Recommendation
Ratify the **cheap-signals first cut**: transfer (pending/running/failed/oldest/stuck via `countByStatus`), OCR
(depth/oldest via the Redis store), mail (account-level fetch-health via `MailAccount.lastFetchAt` / `lastFetchStatus`), surfaced
read-only on an AdminDashboard "Queue Backlog" card, with a display-style stuck threshold. **Defer** the expensive OCR
metadata scan and any risk-level/alerting. It closes the largest remaining ops blind spot after scheduler-run, with
read-only reads against indexed columns / O(1) Redis ops and no control surface.
