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
