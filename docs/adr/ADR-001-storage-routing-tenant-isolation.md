# ADR-001: Storage Routing and Tenant Isolation

**Date:** 2026-04-11 (revisited 2026-05-24)

**Status:** Accepted: Option A (Global Shared Storage)

**Deciders:** Athena ECM Platform Team

## Context

Athena ECM stores uploaded content via `ContentService` (`ecm-core/src/main/java/com/ecm/core/service/ContentService.java`). The current implementation uses a single shared storage tree organized by date:

```
/{rootPath}/{year}/{month}/{day}/{contentId}
```

Content IDs are generated from a timestamp plus UUID (e.g., `20231201120000_abc123def...`), and the year/month/day segments are extracted from that ID to form the directory structure.

**Deduplication.** On upload, `ContentService` computes a SHA-256 hash of the incoming stream. If a document with the same hash already exists (looked up via `DocumentRepository.findByContentHash`), the existing `contentId` is returned and no new file is written. This deduplication operates globally across all tenants.

**Multi-tenancy.** Athena scopes tenants at the application layer using path-based workspace roots. Each tenant's documents live under a workspace root node, and tenant resolution is handled by `TenantContext`. Content storage itself has no awareness of tenants -- all tenants share the same directory tree.

**Quota enforcement.** `TenantQuotaService` (`ecm-core/src/main/java/com/ecm/core/service/TenantQuotaService.java`) enforces per-tenant storage quotas. It resolves the current tenant, finds the tenant's root node path, and sums `fileSize` across all documents under that path prefix via `DocumentRepository.sumFileSizeByPathPrefix`. `ContentService` calls `tenantQuotaService.assertQuotaAvailable(storedSize)` after writing content to disk, rolling back (deleting the file) if the quota is exceeded.

**Production storage.** Production deployments use MinIO (S3-compatible object storage) as the backing store.

The question is whether to introduce per-tenant storage routing to physically isolate tenant content, or to keep the current shared model.

## Decision Options

### Option A: Keep Global Shared Storage (Current)

Retain the existing single directory tree with cross-tenant deduplication.

| Pros | Cons |
|------|------|
| Cross-tenant deduplication reduces total storage | No physical isolation between tenants |
| Simple architecture -- one path scheme, one backup target | Cannot use per-tenant encryption keys |
| Single backup/restore process | Tenant deletion requires scanning all documents to find and remove content; cannot simply delete a directory or bucket |
| Quota enforcement already works at the application layer | Single-tenant backup/restore requires application-level filtering |

### Option B: Per-Tenant Path Routing

Insert the tenant domain into the storage path:

```
/{rootPath}/{tenantDomain}/{year}/{month}/{day}/{contentId}
```

Introduce a `TenantRoutingContentStore` abstraction that resolves the current tenant and delegates to tenant-scoped paths.

| Pros | Cons |
|------|------|
| Physical tenant isolation at the filesystem level | Loses cross-tenant deduplication (same file uploaded by two tenants is stored twice) |
| Per-tenant backup/restore by directory subtree | Requires data migration for existing content |
| Per-tenant encryption possible (encrypt each tenant's subtree with a distinct key) | Adds a `TenantRoutingContentStore` layer of indirection |
| Clean tenant deletion -- remove the tenant's directory | Within-tenant dedup is still possible; cross-tenant dedup is not |

### Option C: Per-Tenant MinIO Buckets

Each tenant receives a dedicated MinIO bucket. Content paths within each bucket follow the existing date-based scheme.

| Pros | Cons |
|------|------|
| All benefits of Option B | Bucket management overhead (create/delete on tenant provisioning/removal) |
| MinIO-level IAM policies per tenant | More complex MinIO configuration and connection management |
| Independent bucket lifecycle rules (retention, replication) | Potential connection-pool-per-bucket resource cost |
| Native S3 bucket-level encryption with distinct keys | Harder to share a single MinIO cluster across many small tenants |

## Decision

**Accepted: Option A.** The global shared storage model is the implemented and supported architecture. No per-tenant storage routing is in flight, and the previous "Deferred" status has been retired because the live codebase has matched Option A continuously since 2026-04-11; describing the same choice as "deferred" misled readers into expecting a pending decision.

The existing architecture is adequate for current operational requirements:

- `TenantQuotaService` provides per-tenant quota enforcement at the application layer.
- Cross-tenant deduplication delivers meaningful storage savings.
- No current customer has requested physical data isolation.

### Reopen Criteria

This ADR should be reopened (revisited and potentially replaced by Option B or C, or by a new ADR) if any of the following observable conditions is met. The criteria are intentionally phrased so a future reviewer can verify them from operational telemetry or written customer commitments, not by judgement alone.

1. **Compliance / data-residency contract** — A signed contract or written customer commitment requires physical data isolation, region-pinned blobs, or attestation that one tenant's data is not co-located with another's at the storage layer (e.g., data residency, government cloud, ITAR, jurisdiction-bound regulators).
2. **Per-tenant encryption requirement** — A written security review or compliance audit names per-tenant content-at-rest key separation as a remediation item. See ADR-003 (content-at-rest encryption) for the orthogonal cryptographic question; ADR-001 reopens only when per-tenant key isolation specifically requires per-tenant storage scope.
3. **Tenant-lifecycle operational pain** — An operational incident report or postmortem identifies tenant deletion / migration as a root cause of downtime, data leakage, or unacceptable runtime cost. The current document-table-scan model is acceptable only while tenant count and turnover stay within the capacity it implies.
4. **Measured dedup return below threshold** — A telemetry-backed analysis shows cross-tenant deduplication savings below an agreed threshold (proposed: < 5% of total storage saved by cross-tenant identical-blob coincidence, measured over a representative window). Note: the codebase as of 2026-05-24 has no telemetry to measure this; introducing the measurement is itself a prerequisite to triggering on this criterion.

### Revisit 2026-05-24

Read-only re-examination performed in `docs/ADR_001_STORAGE_ROUTING_DISCOVERY_20260524.md`. Outcome:

- **No reopen criterion has materialized** between 2026-04-11 and 2026-05-24. No compliance contract, no security-audit remediation, no operational incident, and no dedup-savings measurement.
- **Current live architecture continues to match Option A verbatim.** `ContentService.java:66-123` writes raw bytes via direct Java NIO into the date-sharded path under `ecm.storage.root-path`; `findByContentHash(...)` lookup is unscoped by tenant; `TenantWorkspaceScopeService` is never invoked from `ContentService` or `ContentReferenceService`.
- **Two precision corrections to the original ADR-001 wording**:
  - **`ecm.storage.type: filesystem` is configured in YAML but is not consumed by Java code.** `grep -RIn 'ecm.storage.type' ecm-core/src/main/java` returns zero hits. The property exists as a hint to operators; there is no pluggable `StorageAdapter` interface today. A future Option B or C would have to introduce the abstraction first; absence of it is part of the cost.
  - **"Production deployments use MinIO" (original ADR-001 §25) describes the operational target, not the codebase.** The application talks to local filesystem via `java.nio.file.Files`; MinIO is presumed mounted as a local path (e.g., via FUSE / CSI driver). No S3 SDK or MinIO client is on the `ecm-core` content classpath. Any Option C (per-tenant bucket) implementation would need to introduce the client + abstraction layer as a prerequisite.
- **Content-at-rest plaintext is an orthogonal concern**, surfaced for the first time in writing by the 2026-05-24 discovery (it was named only as a consequence in §93 of the original ADR-001). It is being spun off to **ADR-003: Content-at-rest Encryption** rather than expanded inside this ADR. Per the precedent of ADR-002 (tenant quota accounting), a separable concern earns its own ADR.

This decision is reaffirmed as Accepted: Option A. The decision should be considered live unless and until a Reopen criterion is met and documented in a follow-up ADR or in an addendum to this one.

## Consequences

1. **Quota enforcement remains application-level.** `TenantQuotaService` sums document sizes by path prefix. This is a soft boundary -- it does not prevent direct filesystem writes outside the application.
2. **Tenant deletion requires document scanning.** Removing a tenant's content requires querying all documents under the tenant's workspace path and deleting the corresponding content files individually. There is no directory or bucket to simply drop.
3. **Shared encryption key.** If encryption at rest is enabled (e.g., MinIO server-side encryption), all tenant content is encrypted with the same key. Per-tenant key isolation is not possible without adopting Option B or C.
4. **Cross-tenant dedup is preserved.** Identical files uploaded by different tenants consume storage only once, which reduces total storage costs.
5. **Backup/restore granularity.** Full backups are straightforward (back up the entire storage tree). Single-tenant backup or restore requires application-level filtering to identify the content IDs belonging to that tenant's documents.
