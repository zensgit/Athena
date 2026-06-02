# Q2 Brief v2 — TenantContext for async/background content writers (2026-06-01, gate-revised)

Read-only inventory + implementation brief for ADR-002 Next Step #2. **No code in this brief.**
**v2 supersedes v1.** Gate review found two errors in v1: (1) `BulkOperationService` is NOT async,
and (2) v1 missed the `@Scheduled` background writers. Corrected below.

## Inventory — paths that write repository content OFF the request thread

Two distinct classes, with **different** fixes:

### Class A — request-thread-submitted async (a request tenant exists to capture)
| Path | Mechanism | Write |
|---|---|---|
| **`BulkImportService:115`** `importExecutor.execute(processImportJob)` | bare `Executors.newCachedThreadPool()` (`:64`) | → `uploadDocument` → `ContentService.storeContent` (quota) |

Only one. A request thread (holding TenantContext) submits to a worker — the snapshot/restore pattern fits.

### Class B — scheduled/system background writers (NO request tenant to capture)
| Path | Mechanism | Write |
|---|---|---|
| **`RmReportPresetDeliveryService:287`** `@Scheduled(cron)` | scheduler thread | `:422` `uploadDocument` (CSV) |
| **`MailReportScheduledExportService:63`** `@Scheduled(cron)` | scheduler thread | `:101` `uploadDocument` (report) |
| **`MailFetcherService:122`** `@Scheduled(fixedDelay)` | scheduler thread | `:2438` `uploadDocument` (attachment ingest) |
| **`DirectoryWatcherService:72`** `@Scheduled(fixedDelay)` | scheduler thread | `:101` `uploadDocument` (watched file) |

These start from a scheduler tick, not a request — there is **no request tenant to snapshot**. The
snapshot/wrapper pattern does **not** solve them.

### Out of scope (corrected)
- **`BulkOperationService`** — `OperationExecutor` is a **local `@FunctionalInterface`** (`:152`), and `executor.execute(id)` (`:86`) runs **synchronously in the request-thread for-loop** (`:79-90`). NOT async — removed. (If bulk copy under-enforces quota, that is a *synchronous* quota/tenant contract, not async propagation.)
- **Transfer receiver** (`TransferReceiverController.uploadDocument`): synchronous HTTP request thread.
- **Transfer sender** (`TransferReplicationService.processJob`): reads + sends to remote, no local store.
- **Export `CompletableFuture` tasks** (8 sites): write export files, not repo content; `requestSnapshot` is a request-DTO copy, not a tenant snapshot.

## Gap

`TenantContext` is a ThreadLocal (`tenantDomain` + `tenantRootNodeId`) with only set/get/clear — no
snapshot/restore. Both classes lose tenant identity off the request thread, but they need **different**
fixes, so they must be split.

## Plan — split per gate

### Q2a — BulkImport submit-time snapshot (small, directly codeable)
1. `TenantContext.capture()` → immutable snapshot; `restore(snapshot)`; pair with `clear()`.
2. Wrap `BulkImportService.importExecutor` (or the submitted Runnable): capture at submit time, `restore` in the worker, `finally clear()`.
3. Tests (bulk import): submit thread sets **T** → worker sees `T` → after the task the worker is cleared → a reused pool thread does **not** inherit `T` → quota enforced against `T`.

### Q2b — scheduled writers: tenant source-of-truth (discovery/brief first, NOT snapshot)
Scheduled jobs have no request tenant. **Before any code**, decide the authoritative tenant source per writer:
- **config-pinned tenant** (the job runs for a configured tenant), or
- **derive tenant from the target folder** (reverse-lookup the tenant root from the destination `folderId` the write already has), or
- **disable / single-tenant-only** these features until a multi-tenant decision is made.

This is a design decision, not a propagation mechanism. Q2b = a separate discovery doc that picks the
source-of-truth per writer, then implements. Do **not** paper over it with the snapshot primitive.

## Recommendation

Open **Q2a** (BulkImport snapshot/restore/clear) as the next codeable slice — small, request→worker, testable.
Keep **Q2b** (the 4 `@Scheduled` writers) as a separate discovery: they share the "no request tenant"
problem and need a tenant source-of-truth decision, not a snapshot.
