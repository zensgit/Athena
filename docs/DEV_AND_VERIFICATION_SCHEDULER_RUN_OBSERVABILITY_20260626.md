# Scheduler-Run Observability — Development & Verification

- Date: 2026-06-26
- Line: Athena observability for the recurring `@Scheduled` jobs (the scheduler blind spot).
- State: **COMPLETE on `main`.** taskbook → ratify (incl. §3A guards) → build → review → CI 7/7.
- Repos: Athena only. **Observability-only (NOT a control plane); no scheduler-logic change.**

## 1. State at a glance

The ~24 recurring `@Scheduled` jobs (mail fetch, ldap sync, transfer replication, the 02:00-04:00 nightly
cleanups, etc.) had **no admin-visible run status**. This adds a read-only surface: per-job **last-run / status /
duration / last-error-type / next-run**, captured by an `@Around` aspect (no per-scheduler edits), surfaced at an
admin endpoint + an AdminDashboard "Scheduled Jobs" card. Observability-only — **not** a control plane.

| PR | What | Merge SHA |
|---|---|---|
| #40 | Day-1 taskbook — gap audit + ratified candidate #1 + §6 + the §3A guards | `424b106` |
| #41 | Implementation — registry + aspect + service + endpoint + dashboard + tests | `5739867` |

## 2. The gap (recap)

The async governance layer (`asynctask/`) covers **6 on-demand task domains** (audit / ops / search / preview /
batch-download / property-encryption) + an AdminDashboard "Async Task Health" panel — but it is task **counts**, not
scheduler **timing**. The ~24 recurring `@Scheduled` methods (across ~22 services) were the blind spot — no unified
last-run / next-run / duration / success-fail surface. "Did the nightly cleanups run? Is mail fetch failing silently?" →
only via SSH + logs + DB.

## 3. What was built (code-grounded)

- **`SchedulerRunRegistry`** — in-memory, since-boot (`ConcurrentHashMap` of immutable `RunRecord`, atomic via
  `compute`); per `jobId`: `lastRunAt`, `lastStatus` (RUNNING/SUCCESS/FAILED), `lastDurationMs`, `lastErrorType`
  (exception class only — per the sensitive-data logging line), `runCount`, `failCount`.
- **`ScheduledRunAspect`** — `@Around("@annotation(...Scheduled)")` → recordStart → `proceed()` → record
  success / failure(type) and **rethrow the original Throwable unchanged** (§3A.1). Adds `spring-boot-starter-aop`
  (the aspect's prerequisite — was absent; no prior `@Aspect`).
- **`SchedulerObservabilityService`** — joins the registry (runs) with Spring `ScheduledTaskHolder` (schedules) on
  the stable `jobId`. `nextRunAt` computed only where well-defined — cron via `CronExpression.next`, fixedRate from
  `lastRun + interval`; **fixedDelay → null** + a `scheduleDescription` (§3A.2); never throws; a defensive pass
  surfaces any registry-only job.
- **`SchedulerJobIds`** — stable proxy-safe `declaringClass#methodName` (unwrap via `AopUtils.getTargetClass` + strip
  the CGLIB `$$` marker; param-type suffix for overloads). The **same** helper feeds the aspect and the service so
  run + schedule JOIN on one id (§3A.3).
- **`SchedulerJobSnapshotDto`** — `jobId, lastRunAt, lastStatus, lastDurationMs, lastErrorType, runCount, failCount,
  nextRunAt (nullable), scheduleDescription (always non-null)`.
- **`SchedulerAdminController`** — `GET /api/admin/schedulers` (+ `/api/v1/...`), `@PreAuthorize hasRole('ADMIN')`,
  returns the snapshot list. **Observability-only — no control endpoints.**
- **Frontend `ScheduledJobsCard`** on `AdminDashboard` — table (job, last-run, next-run / scheduleDescription,
  status chip, duration, last error type); **failure-isolated** fetch (can't break the async-health panel).

## 4. §3A guards — CI-proven (not just code-read)

- **§3A.1 (real tick + rethrow):** `ScheduledRunObservabilityIntegrationTest` boots a real `@EnableScheduling` +
  `@EnableAspectJAutoProxy` context with actual `@Scheduled(fixedDelay=40)` success/failing beans; it polls the
  registry for SUCCESS/FAILED from the **real scheduler firing** (not a direct call); a custom `TaskScheduler`
  `ErrorHandler` captures the rethrown `IllegalStateException` → proves the aspect rethrows unchanged. Type-only
  asserted (`!contains("SENSITIVE")`).
- **§3A.2 (no false precision):** the service computes `nextRunAt` only for cron + fixedRate; **fixedDelay → null** +
  a non-null `scheduleDescription`; never throws / never fabricates. Asserted (cron computed; fixedDelay null).
- **§3A.3 (stable, proxy-safe jobId):** `SchedulerJobIds` unwraps CGLIB; `SchedulerJobIdsTest` + the integration
  test's id-matching prove it (the recorded ids match the real class, not a proxy).

## 5. Verification

**CI — #41 7/7 green** (run `28214250818`): Acceptance Smoke, Backend Verify, Frontend Build & Test, Frontend E2E
Core Gate, Phase 5 Mocked Regression Gate, Phase C Security Verification, Property Encryption Closeout Gate. #40 7/7.

**Tests:** the integration test (§3A.1 + §3A.2), `SchedulerJobIdsTest` (§3A.3), `SchedulerAdminControllerSecurityTest`
(admin-gate, CLAUDE.md convention), `SchedulerAdminControllerTest` (shape), `ScheduledJobsCard.test.tsx` (render +
failure-isolation).

**Local targeted verification:** `./mvnw -Dtest=SchedulerJobIdsTest,ScheduledRunObservabilityIntegrationTest,`
`SchedulerAdminControllerTest,SchedulerAdminControllerSecurityTest test` passed locally during closeout review
(9 tests / 0 failures), in addition to CI 7/7. CI also earned its keep by catching 3 issues across iterations, each
fixed from the CI log:
1. `IntervalTask.getInterval()` returns `long` (not `Duration`) in this Spring version → `Duration.ofMillis(...)`.
2. `AopUtils.isCglibProxyClass(Class)` is absent → rely on `getTargetClass` unwrap + the `$$` strip.
3. The frontend test used `toBeInTheDocument` but `@testing-library/jest-dom` is not a dependency → rewrote to the
   project's `toBeTruthy()` / `toBeNull()` convention.
Then 7/7 green. **Coverage check:** a robust re-count finds ~24 `@Scheduled` methods across ~22 services, and every
one is annotated on a `public` method (verified — no `@Scheduled` on a private/protected method), so the proxy-based
aspect advises them all. A package-private `@Scheduled` added later would not be proxy-advised — a known Spring AOP
limitation worth a check when new schedulers land.

## 6. Boundaries / out of scope

- **Observability-only — NO control plane** (no cancel / trigger / pause). The async-task control plane is a separate,
  deferred decision.
- No scheduler-logic change (capture via the aspect only); type-only errors (class, no message/stack); reuse the
  AdminDashboard (failure-isolated card); the 6 governance domains / quota / storage / logging untouched; no
  retry-strategy change, no new worker, no alerting.
- In-memory since-boot (no run-history persistence / cross-instance aggregation — a separate, bigger slice).
- **Re-entry** would each be a *new* taskbook: a control plane (cancel/trigger), persisted run-history, queue-depth
  for OCR / mail (Day-1 candidate #2), cross-subsystem dead-letter aggregation (#3), or threshold alerting.

## 7. Conclusion

Scheduler-run observability is complete and verified on `main` (taskbook `424b106` → implementation `5739867`).
Operators now see, read-only, the last-run / next-run / status / duration / last-error of every recurring
`@Scheduled` job — closing the recurring-scheduler run-timing blind spot — captured by a single `@Around` aspect with **no
scheduler-logic change**, honest next-run semantics, and a stable proxy-safe job id, all CI-proven against the
ratified §3A guards. The control plane (cancel/trigger) and the queue-depth / dead-letter candidates remain separate,
deliberate decisions.
