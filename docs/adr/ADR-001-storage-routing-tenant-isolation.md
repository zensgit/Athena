# ADR-001: Storage Routing and Tenant Isolation

**Date:** 2026-04-11

**Status:** Deferred

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

**Deferred.** The current global shared storage model (Option A) is retained. No per-tenant storage routing will be implemented at this time.

The existing architecture is adequate for current operational requirements:

- `TenantQuotaService` provides per-tenant quota enforcement at the application layer.
- Cross-tenant deduplication delivers meaningful storage savings.
- No current customer has requested physical data isolation.

### Trigger Conditions to Revisit

This decision should be re-opened if any of the following conditions arise:

1. **Regulatory or compliance requirement** -- A customer or market requires physical data isolation (e.g., data residency, government cloud, ITAR).
2. **Per-tenant encryption** -- A requirement emerges for encrypting each tenant's content with a distinct key (at-rest key separation).
3. **Tenant lifecycle at scale** -- Tenant deletion or migration becomes operationally painful because content cleanup requires scanning the entire document table rather than removing a directory or bucket.
4. **Diminishing dedup returns** -- Analysis shows cross-tenant deduplication savings are negligible (most tenants upload unique content), eliminating the primary benefit of shared storage.

## Consequences

1. **Quota enforcement remains application-level.** `TenantQuotaService` sums document sizes by path prefix. This is a soft boundary -- it does not prevent direct filesystem writes outside the application.
2. **Tenant deletion requires document scanning.** Removing a tenant's content requires querying all documents under the tenant's workspace path and deleting the corresponding content files individually. There is no directory or bucket to simply drop.
3. **Shared encryption key.** If encryption at rest is enabled (e.g., MinIO server-side encryption), all tenant content is encrypted with the same key. Per-tenant key isolation is not possible without adopting Option B or C.
4. **Cross-tenant dedup is preserved.** Identical files uploaded by different tenants consume storage only once, which reduces total storage costs.
5. **Backup/restore granularity.** Full backups are straightforward (back up the entire storage tree). Single-tenant backup or restore requires application-level filtering to identify the content IDs belonging to that tenant's documents.
