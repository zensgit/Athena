# Q2 Brief — TenantContext propagation into async content-writing paths (2026-06-01)

Read-only inventory + implementation brief for ADR-002 Next Step #2. **No code in this brief.**
Q1 (quota model) is closed and CI-green; Q2 propagates tenant identity into async/background paths
that write content, so quota (and tenant scoping) is enforced against the right tenant once work
leaves the request thread.

## Inventory — content-writing async paths (quota-relevant)

Scanned every async entrypoint (Executors / `@Async` / `CompletableFuture` / `executor.execute`).
Only these write repository content off the request thread, so only these depend on TenantContext
for quota/scoping:

| Path | Async mechanism | Content write | TenantContext today |
|---|---|---|---|
| **`BulkImportService:115`** `importExecutor.execute(processImportJob)` | bare `Executors.newCachedThreadPool()` (`:64`) | `processImportJob` → `documentUploadService.uploadDocument` → `ContentService.storeContent` (quota) | **LOST** — worker thread, ThreadLocal not propagated |
| **`BulkOperationService:86`** `executor.execute(id)` | executor | COPY → `nodeService.copyNode` (creates node: logical usage + tenant scoping); MOVE → `moveNode` (scoping) | **LOST** — worker thread |

### Out of scope (confirmed by reading the paths)
- **Transfer receiver** (`TransferReceiverController.uploadDocument`): synchronous HTTP request thread — has TenantContext. OK.
- **Transfer sender** (`TransferReplicationService.processJob`, `:288/456`): reads the source node and sends to the remote receiver; no local content store (quota is enforced on the receiver). Tenant scoping affects the *read*, not quota — a separate concern, not quota-Q2.
- **Export tasks** (`CompletableFuture.runAsync` in Analytics/Search/OpsRecovery/PreviewDiagnostics, 8 sites): write export files, not repository content → no quota. Their `requestSnapshot` is a copy of the **request DTO**, NOT a TenantContext snapshot.

## Gap

`TenantContext` (`config/TenantContext.java`) is a ThreadLocal holding `tenantDomain` + `tenantRootNodeId`.
Static API today: `set/get` for each + `clear()`. **There is no snapshot/restore helper**, so any work
that leaves the request thread silently loses tenant identity. `BulkImportService` uses a bare
`newCachedThreadPool()` whose threads are reused — a stale tenant could even leak between jobs.

## Implementation brief (Q2 slice — NOT done here)

1. **Add a TenantContext snapshot primitive** — capture both `tenantDomain` + `tenantRootNodeId`:
   `TenantContext.capture()` → immutable snapshot; `TenantContext.restore(snapshot)`; pair with `clear()`.
2. **Executor wrapper** (preferred over per-call lambdas — reusable, impossible to forget): a
   `TenantAwareExecutor` that captures the snapshot at `execute()` submit time and, in the worker,
   does `restore` → run → `finally clear()`. Wrap `BulkImportService.importExecutor` and
   `BulkOperationService.executor`.
3. **Apply** to `BulkImportService:115` and `BulkOperationService:86`.

## Test plan (start with bulk import, per gate)

- Submit thread sets tenant **T**; worker thread inside the task sees `getCurrentTenantDomain() == T` (restore works).
- After the task, the worker's TenantContext is cleared (`finally clear()`) — no leak.
- A second task on the same reused pool thread, submitted with a different tenant (or none), does **not** inherit T (no cross-task leak).
- Quota: a bulk import under tenant T enforces against T's usage, not a null/global tenant.

## Scope boundary

Q2 = tenant propagation for the two content-write async paths above + the snapshot/wrapper primitive.
NOT in Q2: export-task scoping (no content write), transfer-sender read-scoping (separate concern),
physical quota (ADR-001 gated, closed in the Q1 addendum).
