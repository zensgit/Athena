# ADR-002 Tenant Quota Accounting And Context Boundaries

- Status: Accepted
- Date: 2026-04-11

## Context

Athena now performs quota checks in request entrypoints and inside `ContentService`, but the current implementation still has unresolved boundary problems:

1. Background and async paths can lose `TenantContext`, so a `ThreadLocal`-only tenant lookup is not authoritative once work leaves the request thread.
2. Quota usage is currently derived from live `Document.fileSize`, which does not necessarily represent the physical storage consumed by version history.
3. `ContentService` can return early on deduplicated content, so quota enforcement must account for the dedup fast path explicitly instead of assuming every write reaches the post-store check.
4. Athena has not yet frozen the accounting model for quota enforcement:
   - logical footprint: current live document sizes only
   - physical footprint: stored blobs plus version history

## Decision

Athena will treat tenant quota enforcement as a scoped platform contract, not as an incidental validation inside upload controllers.

For the current platform phase:

1. We explicitly record that `TenantContext` propagation is required for any asynchronous or scheduled content-writing path.
2. We will not extend quota enforcement further until the accounting model is fixed in code and tests.
3. We will document quota as provisional until Athena chooses one of these models:
   - logical current-document footprint
   - physical blob-plus-version footprint
4. Any future authoritative quota implementation must evaluate:
   - direct uploads
   - version creation
   - bulk import/background ingestion
   - transfer receiver writes
   - deduplicated content reuse

## Consequences

- The current quota guard is useful, but it is not yet a complete storage-governance boundary.
- Version-heavy tenants may be undercounted if Athena continues to derive usage from live document size only.
- Async ingest paths remain a risk until tenant identity is propagated explicitly instead of inferred only from request-thread state.
- Future quota work must be validated against both write-time storage behavior and reporting behavior; otherwise Athena will reject or allow content inconsistently.

## Next Steps

1. Decide and document the quota accounting model: logical vs physical.
2. Propagate tenant identity into async/background execution paths that can store content.
3. Update quota calculation and tests to match the selected model, including version history behavior.
4. Make `ContentService` dedup reuse participate in the same quota contract as non-dedup writes.

**Status (2026-06-02): all four Next Steps closed.** #1 + #3 frozen in the 2026-06-01 addendum (Q1, logical model + version-history accounting); #4 closed by the `ContentService` dedup-fast-path quota enforcement; #2 closed by Q2a + Q2b in the 2026-06-02 addendum. The "provisional" qualifier in the Decision above (item 3) no longer applies — quota is the frozen logical model.

## Addendum (2026-06-01) — model frozen (Q1): logical current + non-current retained versions

The quota accounting model is now frozen:

> **usedBytes = sum(live `Document.fileSize` under tenant root) + sum(non-current retained `Version.fileSize` under tenant root).**

- The current version's bytes equal the live document size and are counted once via the documents sum. The initial version references the current `contentId` (`InitialVersionProcessor`), so `live + all versions` would double-count it; the version sum therefore **excludes each document's `currentVersion`**. Older retained versions add to usage — resolving the version-heavy undercount called out above.
- **No physical blob dedup is applied** — this is *logical* per-tenant accounting. Physical per-tenant blob accounting stays **out of scope**: ADR-001 accepted global shared storage with cross-tenant dedup, which makes per-tenant physical ownership of a shared blob ill-defined. Reopening physical accounting requires reopening ADR-001.
- The `ContentService` dedup fast path now enforces quota with the incoming size, so dedup reuse consumes the same logical quota as a normal write (closes Next Step #4).

Implemented in `TenantQuotaService.calculateUsedBytes` + `VersionRepository.sumNonCurrentVersionFileSizeByPathPrefix` + the `ContentService.storeContent` dedup branch.

**Q2 (Next Step #2) — closed 2026-06-02.** `TenantContext` propagation into async/background content-writing paths is complete (this 2026-06-01 addendum covered the model + synchronous enforcement only). See the 2026-06-02 addendum below for the Q2a + Q2b breakdown.

## Addendum (2026-06-02) — Q2 closed: async/background tenant propagation complete

Next Step #2 is done. The off-request-thread content writers were inventoried (Q2 brief v2) into two classes and fixed separately, because they need different mechanisms:

- **Q2a — request-thread async (snapshot/restore).** `BulkImportService`'s import executor is wrapped by `TenantAwareExecutor`, which captures the submitter's tenant (`TenantContext.capture()`) at submit time, `restore()`s it on the worker, and clears afterward so a pooled thread never inherits a stale tenant. (`595f64c`, full CI green.)
- **Q2b — scheduled/system writers (derive tenant from target folder).** The four `@Scheduled` writers — `DirectoryWatcherService`, `RmReportPresetDeliveryService`, `MailReportScheduledExportService`, `MailFetcherService` — start from a scheduler tick with no request tenant to snapshot. Each resolves the owning tenant from its destination folder via `TenantContextResolverService.resolveTenantForTargetFolder` (parent-chain reverse lookup to a tenant root) and scopes the write with `capture → set → finally restore(previous)`. A target folder not under an enabled tenant root — or a null/missing folder (Option A) — is a configuration error and is **rejected, not written untenanted**: absorb-style writers (Rm, MailReport) record it through their existing failure path (`persistFailedExecution` / `ScheduledExportResult.failed`); throw-style writers (DirectoryWatcher, MailFetcher) surface it as a skip-to-`.error` or an ERROR/FAILED row. (`a2038c8`, `d1dd45b`, `675f43e`, `f52b130`; Backend-Verify CI green.)

A deliberate consequence of resolving once per message (not per write-point, per gate ruling): an empty-content message landing on a misconfigured (non-tenant) folder now surfaces an ERROR/FAILED at resolve time rather than a silent `no_content` skip — a misconfigured target folder is a configuration error regardless of that message's payload. (Gate-ruled 2026-06-02; revisit only as a UX/triage follow-up if operators report it as noisy.)

With Q1 (model) + Q2a/Q2b (async propagation) + version-history accounting + the dedup-fast-path quota contract all in place, **all four Next Steps are closed and the quota model is no longer provisional** — it is the frozen logical model from the 2026-06-01 addendum. Physical per-tenant blob accounting stays out of scope (requires reopening ADR-001).

Confirmed during inventory as NOT async-propagation targets: `BulkOperationService` (synchronous request-thread for-loop), transfer receiver/sender (synchronous / no local store), export `CompletableFuture` tasks (write export files, not repository content).
