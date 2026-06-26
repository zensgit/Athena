# Scheduler / Worker Queue Observability — Day-1 Taskbook / Gap Audit

- Date: 2026-06-25
- Status: **Day-1 gap audit + taskbook. RATIFIED (owner, 2026-06-25): candidate #1 (scheduler-run observability) + §6. Three pre-build guards REQUIRED before #40 merges — see §3A. No code until #40 merges.**
- Scope: Athena observability for the recurring `@Scheduled` jobs (the scheduler blind spot).
- Owner decision requested: the capture mechanism + the surface + the observability-only scope (§3 / §6).

## 0. Executive finding

The async **observability is not absent** — there is a mature `asynctask/` governance layer + an AdminDashboard
"Async Task Health" panel. But it covers exactly **6 on-demand async-task domains** (audit-export, ops-recovery,
search, preview, batch-download, property-encryption). The **~22 recurring `@Scheduled` jobs are the blind spot**:
~18 are entirely invisible — no last-run / next-run / duration / success-fail / last-error reaches any admin
endpoint. So "is the mail fetcher / the nightly archive / disposition / trash / ldap-sync running and succeeding?"
is answerable only by SSH + log-grep + DB queries. **This line = a read-only scheduler-run observability surface,
NOT a control plane.**

## 1. Current code facts (grounded)

### 1.1 The governance layer covers 6 ON-DEMAND domains (not schedulers)
- `asynctask/AsyncTaskGovernanceService` + `AsyncTaskGovernanceProviderRegistry` + `AsyncTaskGovernanceProvider`
  (interface: `order/key/label/getSummary`). 6 `SimpleAsyncTaskGovernanceProvider`s in
  `AsyncTaskGovernanceConfiguration` (orders 10-60): **audit, ops, search, preview, batchDownload, propertyEncryption**.
- Each domain exposes a SUMMARY snapshot (counts: queued / running / completed / failed / cancelled / timedOut /
  expired + risk LOW-CRITICAL). Surfaced at `/analytics/async-governance/overview` + `/tasks` + `/acknowledge`.
- Frontend: AdminDashboard "Async Task Health Overview" panel (≈L2567-2639) + "Recent Async Tasks" (≈L1340-1410) —
  those 6 domains, counts + acknowledge.
- **The governance model is TASK COUNTS, not scheduler TIMING** — even covered domains have no last-run/next-run/duration.

### 1.2 The ~22 `@Scheduled` jobs — ~18 invisible
Recurring schedulers with NO admin-visible run status: `MailFetcherService` (`mail_fetch_*` Micrometer metrics exist
but no admin endpoint), `LdapSyncScheduler`, `MailProcessedRetentionService`, `MailReportScheduledExportService`,
`SanityCheckService`, `ActivityService`-cleanup, `ArchivePolicyScheduler`, `ContentReferenceService`-cleanup,
`DispositionScheduleScheduler`, `NotificationInboxService`-cleanup, `RmReportPresetDeliveryService` (every 5 min),
`ScheduledRuleRunner`, `ShareLinkService`-cleanup, `SiteInvitationService`-cleanup, `TrashService`-purge,
`DirectoryWatcherService`, `TransferReplicationScheduler` (every 5 min + nightly cleanup). Only ~4 feed a covered
domain (preview queue, batch-download cleanup, property-encryption recovery, audit cleanup).

### 1.3 Scattered per-subsystem surfaces (on-demand, not schedulers)
audit-export / ops-recovery / search / batch-download registries (task list + summary + cancel); preview dead-letter
(`PreviewDiagnosticsController` + `RedisScheduledQueueStore` + dead-letter requeue); OCR (per-document status only,
no queue health); transfer-replication (`/replication/jobs` status, no per-job timing). These cover the on-demand /
queue side; **none gives recurring-scheduler run history.**

## 2. The gap (what an admin still cannot see)
1. **No unified scheduler view** — ~18 `@Scheduled` jobs are fire-and-forget (no last-run / next-run / duration /
   success-fail / last-error).
2. **No per-job timing** even for covered domains (governance shows counts, not "when did it last run / did it succeed").
3. Concretely: "did the 02:00-04:00 nightly cleanups run + succeed?", "is mail fetch failing silently?", "is OCR
   backing up?" → only via SSH + logs + DB.

## 2A. Gap table (operator columns)

| Subsystem | queue depth | oldest pending | failed count | last success / failure | stuck threshold | operator action |
|---|---|---|---|---|---|---|
| **~18 recurring `@Scheduled` jobs** (mail / ldap / trash / archive / disposition / rule-runner / share-link / site-invite / rm-report / transfer / …) | n/a (not queues) | n/a | ❌ | ❌ **— the core gap: no last-run/success/fail** | ❌ | ❌ |
| OCR queue (`OcrQueueService` / Redis) | ❌ no endpoint | ❌ | per-doc status only | ❌ | ❌ | requeue per-doc only |
| Mail fetch (`MailFetcherService`) | ❌ | ❌ | Micrometer `mail_fetch_*` only (no admin endpoint) | ❌ | ❌ | ❌ |
| Transfer replication | ❌ | ❌ | ✅ job status | ⚠️ no per-job timing | ❌ | retry job |
| Preview queue + dead-letter | ✅ depth | ⚠️ attempts | ✅ | ✅ (tasks) | ⚠️ TTL / max-entries | requeue / cancel ✅ |
| Audit / Ops / Search / Batch-download (on-demand) | ✅ summary | ⚠️ | ✅ | ✅ (task list) | ❌ | cancel ✅ |
| Property encryption | ✅ (DB) | ❌ | ✅ | ✅ | ❌ | ❌ |

Reading: the on-demand domains are decently covered; the **recurring schedulers have zero last-run/success-fail**,
and the OCR/mail queues have no depth/oldest-pending. The largest operator blind spot is the recurring schedulers.

## 2B. Ranked Day-2 candidates (value / risk / independence)

| Rank | Candidate | User value | Risk | Independence |
|---|---|---|---|---|
| **#1 (recommended)** | **Scheduler-run observability** — last-run / next-run / status / duration / last-error-type for the ~18 invisible `@Scheduled` jobs (§3) | **HIGH** — the biggest blind spot (nightly cleanups, mail fetch, ldap, transfer all fire-and-forget) | **LOW** — read-only `@Around` aspect, no scheduler-logic change | **HIGH** — self-contained registry + endpoint + dashboard card |
| #2 | Queue depth + oldest-pending for the uncovered queues (OCR, mail-fetch, transfer) + a stuck-threshold flag | MEDIUM-HIGH (backlog visibility) | LOW-MEDIUM (read-only Redis/DB queries) | MEDIUM (per-queue; reuses the dashboard) |
| #3 | Cross-subsystem dead-letter aggregation (a unified failed/dead-letter view beyond preview) | MEDIUM | MEDIUM (a unified dead-letter model across heterogeneous subsystems) | LOWER (touches many subsystems) |

**#1 wins on all three axes** — it closes the largest gap with the lowest risk and the cleanest independence. §3 details it.
(Strong out-of-line alternative if you want a pure test-only loop instead: **Folder/Search response-contract tests** —
smaller, but lower operator value than scheduler observability.)

## 3. Recommended slice (read-only scheduler-run observability) — candidate #1, detailed
- **`SchedulerRunRegistry`** (in-memory, since-boot) — one row per `@Scheduled` job: `jobId` (bean.method),
  `lastRunAt`, `lastStatus` (SUCCESS / FAILED / RUNNING), `lastDurationMs`, **`lastErrorType` (exception CLASS only —
  per the sensitive-data logging line)**, `runCount`, `failCount`.
- **Capture via an `@Around` AOP aspect on `@Scheduled` methods** — wraps every scheduled invocation, records
  start/duration/success/exception to the registry, **rethrows** unchanged. **No per-scheduler edits.**
- **Schedule / next-run** from Spring's `ScheduledTaskHolder` (enumerates the registered triggers — cron/fixedDelay) →
  next-run + the cron/interval per job.
- **Read-only admin endpoint** (`GET /api/admin/schedulers` + `/api/v1/...`, `@PreAuthorize hasRole('ADMIN')`) → the
  registry snapshot.
- **Frontend** — a "Scheduled Jobs" section on the AdminDashboard async-health area (job, last-run, next-run, status
  chip, duration, last-error-type). Extend, not a new page; fetch failure isolated.

## 3A. Required Day-2 guards (owner pre-build — non-negotiable)

1. **AOP capture proven by a REAL scheduled tick (not a direct call).** The capture test must run under a Spring
   context with `@EnableScheduling` + a test bean carrying a real `@Scheduled(fixedDelay=…)` method + the aspect, and
   assert that when the scheduler actually fires, the registry records the run — for BOTH a succeeding and a throwing
   method — and that on failure the aspect **rethrows** so Spring's scheduled-task error handling is unchanged (the
   original scheduler behaviour is not altered). A test that directly invokes the annotated method does NOT satisfy this.

2. **`nextRunAt` must avoid false precision.** Compute it only where the trigger semantics are well-defined — cron via
   `CronExpression.next(...)`; fixedRate from a known anchor; fixedDelay only after a completed run (and even then it is
   approximate). When it is not cleanly computable (uncomputable trigger, or fixedDelay before the first run) →
   `nextRunAt = null` + a `scheduleDescription` (e.g. the cron string or `fixedDelay=60s`). The endpoint must **never
   fail** on an uncomputable next-run and **never fabricate** a time. DTO: `nextRunAt` nullable; `scheduleDescription`
   always present.

3. **`jobId` stable + proxy-safe.** Derive it from the TARGET (declaring) class + method — `declaringClass#methodName`,
   unwrapping the proxy via `AopUtils.getTargetClass()` / the actual bean class — **never** the CGLIB /
   `$$SpringCGLIB$$` proxy class name. Handle same-name collisions (same method across beans, or overloads) with a
   deterministic suffix or explicit detection. The same `jobId` scheme must be derivable from BOTH the aspect JoinPoint
   (run data) AND `ScheduledTaskHolder` (schedule / next-run) so the registry can JOIN run + schedule on one stable id.

## 4. Boundaries / out of scope
- **Observability-only — NOT a control plane.** No cancel / trigger / pause of schedulers (that is the separately
  deferred "Async Task Control Plane"; this line only OBSERVES).
- **No scheduler-logic change** — capture via the aspect; the `@Scheduled` methods are untouched.
- **Type-only error capture** (exception class; no message/stack — per the sensitive-data logging line).
- **Reuse the existing AdminDashboard async-health surface**; no new dashboard.
- **Do not touch** the 6 governance domains / the on-demand registries / the quota / storage / logging lines.
- **No change to scheduling cadence or retry strategy; no new worker; no alerting/notification system** — alerting on
  thresholds is a separate line; this is read-only visibility only.
- In-memory since-boot for the first slice; persisted run-history / cross-instance aggregation is a separate (bigger) slice.

## 5. Tests
- `SchedulerRunRegistry`: record SUCCESS / FAILED (`lastErrorType` = class, no message), durations, run/fail counts.
- The aspect (§3A.1): a **real Spring scheduled tick** (under `@EnableScheduling`) of a test `@Scheduled` method that
  succeeds / throws → the registry reflects success/failure, and the aspect **rethrows** so Spring's scheduled-task
  error handling is unchanged. NOT a direct method call.
- `nextRunAt` (§3A.2): an uncomputable trigger → `nextRunAt = null` + a non-null `scheduleDescription`; the endpoint
  neither fails nor fabricates a time.
- `jobId` (§3A.3): stable `declaringClass#methodName` from the unwrapped target (no CGLIB/proxy class name); same-name
  collisions get a deterministic suffix; the same id joins aspect run-data with `ScheduledTaskHolder` schedule-data.
- The endpoint: admin-only (`*SecurityTest`, CLAUDE.md convention); returns one row per registered job; non-admin denied.
- Frontend: the Scheduled Jobs section renders rows + status; a fetch failure is isolated from the async-health panel.

## 6. Ratification checklist
- [x] **Capture** = an `@Around` aspect on `@Scheduled` (no per-scheduler edits) — vs a per-scheduler helper.
- [x] **Surface** = a dedicated scheduler-runs snapshot + admin endpoint + an AdminDashboard "Scheduled Jobs" section
      (scheduler obs = TIMING, distinct from the counts-based governance domains) — vs shoehorning into a 7th governance domain.
- [x] **Scope** = OBSERVABILITY-ONLY (last-run / next-run / status / duration / last-error-type); **NO control**
      (cancel/trigger) — control stays the deferred Async Task Control Plane.
- [x] **Storage** = in-memory since-boot for the first slice (persisted history is a later slice).
- [x] **Error capture** = type-only (no message/stack).

## 7. Recommendation
Ratify the read-only scheduler-run observability slice: an `@Around` aspect on `@Scheduled` → `SchedulerRunRegistry`
(last-run / status / duration / last-error-type + next-run via `ScheduledTaskHolder`) → an admin-only endpoint → an
AdminDashboard "Scheduled Jobs" section. It closes the ~18-scheduler blind spot (the #1 ops gap here) with **no
scheduler-logic change and no control surface** — the control plane remains a separate, deliberate decision.
