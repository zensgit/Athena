# Athena Gap-Closure Changelog

**Date**: 2026-04-11
**Scope**: Transfer replication, Multi-tenancy, CMIS bridge, Frontend adaptation
**Commits**: 22 | **Files changed**: 115 | **Lines added**: ~11,500

---

## Transfer & Replication

### Active Job Mutual Exclusion
- Replaced in-memory `findAll().stream()` scan with indexed `existsByDefinitionIdAndStatusIn()` DB query
- Added composite index on `replication_jobs(definition_id, status)`

### Repository Identity
- `RepositoryIdentityProvider` — config-backed single source of truth for CMIS and Transfer identity
- `ecm.cmis.repository-id` (default "athena"), `ecm.transfer.repository-id` (defaults to CMIS value)
- Transfer verify response now includes `repositoryId`; persisted as `remoteRepositoryId` on targets

### Transfer Node Mapping
- `transfer_node_mappings` table with receiver-root scoped unique key
- `TransferNodeMappingService`: upsert, find, refresh sync timestamps
- Enables source→target tracking across repositories

### Delta Watermark
- `lastSuccessfulSyncAt` on replication definitions — set on success, preserved on failure
- First run: full subtree. Subsequent runs: only `lastModifiedDate > watermark`
- v1 scope: create/update/move/rename captured; delete propagation excluded

### Receiver-Side Idempotency
- 4-rule matrix: unchanged→skip, changed→update, missing→recreate, unmapped→conflict policy
- Mapping match takes precedence over name-based collision handling
- Both LoopbackTransferClient and AthenaTransferHttpClient support watermark + mapping

### Per-Entry Job Report
- `entry_report` JSONB on replication_jobs with `{totalEntries, successCount, failureCount, entries}`
- Capped at 5,000 entries with `reportTruncated` flag
- Compact success entries, full failure entries with message

---

## Multi-Tenancy

### Quota Enforcement
- `TenantQuotaService` with dual-layer check: preflight at upload controllers + authoritative in `ContentService.storeContent()`
- Blob cleanup on post-write quota failure
- `QuotaExceededException` with tenant/quota/used/requested details

### Security Cache Isolation
- Permission cache key now includes tenant domain prefix
- Prevents cross-tenant cache pollution on tenant switch

### Tenant Metrics
- `TenantMetricsService`: storage used, quota, available bytes, node/document/folder counts
- `GET /api/admin/tenants/{domain}/metrics` endpoint

---

## CMIS Bridge

### Secondary Types
- `cmis:secondaryObjectTypeIds` exposed from node aspects (sorted)
- Aspect definitions registered as CMIS secondary types in type system
- Add/remove via `updateProperties` with `cmis:secondaryObjectTypeIds` key

### Version History
- `getAllVersions(objectId)` — full version chain as ObjectEntry list
- `getLatestVersion(objectId, major)` — most recent version
- Version objectId format: `documentId;vMajor.Minor`

### Change Log
- `CmisChangeLogService` backed by AuditLog
- ISO-8601 change token from `eventTime`
- Maps: NODE_CREATED→created, NODE_UPDATED→updated, NODE_DELETED→deleted, VERSION_CREATED→updated

### ACL Mapping
- `CmisAclService`: READ→cmis:read, WRITE→cmis:write, DELETE→cmis:all
- `getAcl(objectId)` and `applyAcl(objectId, addAces, removeAces)`
- ACEs grouped by (principal, isDirect)

### Relationships
- `NodeRelation` entity generalizes `DocumentRelation` to support any node type
- `CmisRelationshipService`: getObjectRelationships (source/target/either), create, delete
- `DocumentRelationService` delegates to `NodeRelationService`, marked `@Deprecated`

### Renditions
- `CmisRenditionService` bridges `RenditionResourceService`
- Filter support: `cmis:none`, `*`, exact mime, wildcard (`image/*`), rendition key
- Only available renditions exposed

### Query Language (CMIS-QL)
- `CONTAINS('term')` — PostgreSQL full-text search via `to_tsvector`/`plainto_tsquery`
- `IN_TREE('ref')` — recursive descendants via `path LIKE folderPath/%`
- All WHERE fragments freely combinable with AND

---

## Frontend

### Tenant Admin Page
- Storage usage progress bar (used/quota/available) per tenant card
- IntersectionObserver lazy-loading for metrics

### Transfer Replication Page
- Expandable per-entry report table in job details
- Source path, action, target node, message per entry with summary counts

### CMIS Explorer Page (`/admin/cmis-explorer`)
- Repository Info tab — metadata display
- Type Browser tab — base + secondary types table
- Query Console tab — CMIS-QL input with dynamic result rendering

### Tenant Metrics Dashboard (`/admin/tenant-metrics`)
- Summary cards: total storage, total documents, total nodes
- Recharts bar chart: per-tenant storage used vs quota
- Recharts pie chart: document count distribution

---

## Documentation

| Document | Lines | Content |
|----------|-------|---------|
| `docs/BACKEND_DESIGN.md` | 309 | Architecture, API reference, data models |
| `docs/BACKEND_VERIFICATION.md` | 236 | Test coverage matrix (28 classes, ~189 tests) |
| `docs/adr/ADR-001-storage-routing-tenant-isolation.md` | 95 | Storage routing decision (deferred) |
| `docs/CHANGELOG-gap-closure.md` | this file | Change summary |

---

## Database Migrations

| ID | Table | Change |
|----|-------|--------|
| 066 | replication_jobs | Composite index (definition_id, status) |
| 067 | transfer_targets | remote_repository_id column |
| 068 | transfer_node_mappings | New table with unique key |
| 069 | replication_jobs | entry_report JSONB + report_truncated |
| 070 | replication_definitions | last_successful_sync_at column |
| 071 | node_relations | New table + data migration from document_relations |

---

## Test Coverage

| Area | New Test Files | New Tests |
|------|---------------|-----------|
| Transfer | 3 | ~25 |
| Multi-Tenancy | 3 | ~13 |
| CMIS | 8 | ~65 |
| Frontend | 3 | ~15 |
| **Total** | **17** | **~118** |

All tests pass. Pre-existing failures in unrelated test classes (NodeServicePropertyEnforcement, etc.) are unchanged.
