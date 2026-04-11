# Athena ECM Backend Design Document

## Overview

This document covers the backend features implemented as part of the Athena gap-closure roadmap, bridging capabilities toward Alfresco Community Repo parity while maintaining Athena-native architecture patterns.

---

## 1. Transfer & Replication

### 1.1 Architecture

```
TransferReplicationService (orchestrator)
    |
    +-- TransferClient (interface)
    |       +-- LoopbackTransferClient (local copy)
    |       +-- AthenaTransferHttpClient (remote HTTP)
    |
    +-- TransferReceiverService (inbound receiver)
    |
    +-- TransferNodeMappingService (source-target mapping)
    |
    +-- RepositoryIdentityProvider (config-backed identity)
```

### 1.2 Repository Identity

| Config Key | Default | Purpose |
|-----------|---------|---------|
| `ecm.cmis.repository-id` | `"athena"` | CMIS repository ID (backward-compatible) |
| `ecm.transfer.repository-id` | `${ecm.cmis.repository-id}` | Transfer source identity |

`RepositoryIdentityProvider` is the single source of truth. Both CMIS and Transfer subsystems consume it.

### 1.3 Active Job Mutual Exclusion

**Before**: `replicationJobRepository.findAll().stream()` — full table scan in memory.

**After**: `existsByDefinitionIdAndStatusIn(UUID, Collection<Status>)` — indexed DB query.

Composite index: `(definition_id, status)` via Liquibase migration 066.

### 1.4 Transfer Node Mapping

**Table**: `transfer_node_mappings` (migration 068)

| Column | Type | Purpose |
|--------|------|---------|
| `root_folder_id` | UUID | Receiver root scope |
| `source_repository_id` | VARCHAR(255) | Source repo identity |
| `source_node_id` | UUID | Source node UUID |
| `local_node_id` | UUID | Mapped local node |
| `last_source_modified_at` | TIMESTAMP | Source version tracking |
| `last_synced_at` | TIMESTAMP | Sync timestamp |

**Unique key**: `(root_folder_id, source_repository_id, source_node_id)` — receiver-root scoped.

**Service API**:
- `upsertMapping()` — create or update
- `findMapping()` — lookup by scope
- `refreshSyncTimestamps()` — update timestamps on unchanged nodes

### 1.5 Delta Watermark

**Field**: `ReplicationDefinition.lastSuccessfulSyncAt` (migration 070)

**Semantics**:
- First run (`null`): full subtree replication
- Subsequent runs: only nodes where `lastModifiedDate > lastSuccessfulSyncAt`
- Updated only on successful completion (not on failure)

**v1 Coverage**:
- Included: create, property update, content update, move, rename (all trigger `@LastModifiedDate`)
- Excluded: delete propagation, permission-only changes

### 1.6 Receiver-Side Idempotency (4 Rules)

| Condition | Action |
|-----------|--------|
| Mapping exists + source timestamp unchanged | Return UNCHANGED, refresh sync timestamps |
| Mapping exists + target exists + source changed | Update mapped node in place |
| Mapping exists + target missing | Recreate under receiver root, refresh mapping |
| No mapping | Apply conflict policy (RENAME/SKIP/OVERWRITE), create mapping after success |

Mapping match always takes precedence over name-based collision handling.

### 1.7 Per-Entry Job Report

**Fields on `replication_jobs`** (migration 069):
- `entry_report` — JSONB with structure: `{totalEntries, successCount, failureCount, entries: [...]}`
- `report_truncated` — boolean, set when entries exceed 5000 cap

**Entry shape**: `{sourceNodeId, sourcePath, sourceType, targetNodeId, action, message, startedAt, completedAt}`

Success entries are compact; failure entries preserve full message.

---

## 2. Multi-Tenancy Enhancements

### 2.1 Tenant Quota Enforcement

**Service**: `TenantQuotaService`

**Dual-layer check**:

| Layer | Location | Mechanism |
|-------|----------|-----------|
| Preflight | Upload controllers | `assertQuotaAvailable(file.getSize())` — fast reject before I/O |
| Authoritative | `ContentService.storeContent()` | Check actual stored bytes, delete blob on failure |

**Entrypoints covered**: `DocumentController.uploadDocumentLegacy()`, `UploadController.uploadDocument()`, `UploadController.uploadBatch()` (total batch size).

**Exception**: `QuotaExceededException extends IllegalArgumentException` — includes tenantDomain, quotaBytes, usedBytes, requestedBytes.

### 2.2 Security Cache Tenant Isolation

**Before**: `key = nodeId + "_" + permissionType + "_" + username`

**After**: `key = tenantDomain + "_" + nodeId + "_" + permissionType + "_" + username`

Implemented via `SecurityService.permissionCacheKey()` static method using `TenantContext.getCurrentTenantDomain()`.

### 2.3 Tenant Metrics

**Service**: `TenantMetricsService`

**Endpoint**: `GET /api/admin/tenants/{domain}/metrics`

**Response**:
```json
{
  "tenantDomain": "acme",
  "tenantName": "Acme Corp",
  "enabled": true,
  "storageUsedBytes": 5242880,
  "quotaBytes": 10485760,
  "storageAvailableBytes": 5242880,
  "nodeCount": 150,
  "documentCount": 120,
  "folderCount": 30
}
```

Counts use `path LIKE rootPath/%` pattern, same as quota calculation.

---

## 3. CMIS Bridge

### 3.1 Design Principle

All CMIS operations bridge existing Athena services — no separate CMIS storage or data model.

```
CmisBrowserController (HTTP dispatch)
    |
    +-- CmisBrowserService (object/children/versions)
    +-- CmisQueryService (CMIS-QL parsing + JPA specs)
    +-- CmisMutationService (create/update/delete)
    +-- CmisContentVersioningService (content streams, checkout/checkin)
    +-- CmisChangeLogService (audit-backed change tracking)
    +-- CmisAclService (permission mapping)
    +-- CmisRelationshipService (node relations bridge)
    +-- CmisRenditionService (preview/thumbnail bridge)
    +-- CmisObjectFactory (node → ObjectEntry mapping)
    +-- CmisTypeManager (type system + secondary types from aspects)
```

### 3.2 Browser Binding Selectors (GET)

| Selector | Service | Backing |
|----------|---------|---------|
| `repositoryInfo` | CmisBrowserService | RepositoryIdentityProvider |
| `typeChildren` | CmisBrowserService → CmisTypeManager | AspectDefinitionRepository |
| `object` | CmisBrowserService | NodeService |
| `children` | CmisBrowserService | NodeService, FolderService |
| `query` | CmisQueryService | NodeRepository, DocumentRepository |
| `content` | CmisContentVersioningService | ContentService |
| `versions` | CmisBrowserService | VersionService |
| `latestVersion` | CmisBrowserService | VersionService |
| `contentChanges` | CmisChangeLogService | AuditLogRepository |
| `acl` | CmisAclService | SecurityService |
| `relationships` | CmisRelationshipService | NodeRelationService |
| `renditions` | CmisRenditionService | RenditionResourceService |

### 3.3 Browser Binding Actions (POST)

| Action | Service |
|--------|---------|
| `createFolder` | CmisMutationService → FolderService |
| `createDocument` | CmisMutationService → NodeService |
| `updateProperties` | CmisMutationService → NodeService (+ aspect add/remove) |
| `deleteObject` | CmisMutationService → NodeService |
| `setContentStream` | CmisContentVersioningService → VersionService |
| `checkOut` | CmisContentVersioningService → NodeService |
| `checkIn` | CmisContentVersioningService → NodeService |
| `cancelCheckOut` | CmisContentVersioningService → NodeService |
| `POST /acl` | CmisAclService → SecurityService |
| `POST /relationships` | CmisRelationshipService → NodeRelationService |
| `DELETE /relationships` | CmisRelationshipService → NodeRelationService |

### 3.4 Secondary Types

Node aspects → `cmis:secondaryObjectTypeIds` (sorted list of aspect names).

`CmisTypeManager.getAllTypes()` combines base types (`cmis:folder`, `cmis:document`, `cmis:secondary`) with dynamic secondary types from `AspectDefinitionRepository`.

Add/remove via `updateProperties` with key `cmis:secondaryObjectTypeIds`.

### 3.5 Version History

| Operation | Method | Backing |
|-----------|--------|---------|
| `getAllVersions(objectId)` | CmisBrowserService | VersionService.getVersionHistory() |
| `getLatestVersion(objectId, major)` | CmisBrowserService | VersionService.getVersionHistory(id, majorOnly) |

Version objectId format: `documentId;vMajor.Minor` (CMIS convention).

### 3.6 Change Log

**Service**: `CmisChangeLogService`

**Token**: ISO-8601 timestamp from `AuditLog.eventTime` (monotonic ordering).

**Event mapping**:
| Athena Event | CMIS Change Type |
|-------------|-----------------|
| NODE_CREATED | created |
| NODE_UPDATED | updated |
| NODE_DELETED | deleted |
| VERSION_CREATED | updated |

### 3.7 ACL Mapping

**Frozen mapping**:
| Athena PermissionType | CMIS Permission |
|----------------------|-----------------|
| READ | cmis:read |
| WRITE, CREATE_CHILDREN, CHECKOUT, CHECKIN, CANCEL_CHECKOUT | cmis:write |
| DELETE, DELETE_CHILDREN, CHANGE_PERMISSIONS, TAKE_OWNERSHIP, EXECUTE, APPROVE, REJECT | cmis:all |

ACEs grouped by `(principal, isDirect)`. Inherited permissions marked `isDirect=false`.

### 3.8 Relationships

**Domain model**: `NodeRelation` (generalized from `DocumentRelation`)
- Supports document↔document, folder↔folder, folder↔document
- `DocumentRelationService` delegated to `NodeRelationService`, marked `@Deprecated`

**CMIS operations**:
- `getObjectRelationships(objectId, direction, typeId)` — direction: "source", "target", "either"
- `createRelationship(sourceId, targetId, relationshipType)`
- `deleteRelationship(sourceId, targetId, relationshipType)`

### 3.9 Renditions

**Service**: `CmisRenditionService` bridges `RenditionResourceService`

**Filters**: `cmis:none` (empty), `*` (all), exact mime type, wildcard (`image/*`), rendition key.

Only available renditions are exposed.

### 3.10 Query Language (CMIS-QL)

**Supported syntax**:
```sql
SELECT * FROM cmis:document|cmis:folder
WHERE [IN_FOLDER('id-or-path')]
  AND [IN_TREE('id-or-path')]
  AND [CONTAINS('search terms')]
  AND [cmis:name = 'value']
  AND [cmis:name LIKE 'pattern']
ORDER BY cmis:name|cmis:lastModificationDate|cmis:creationDate [ASC|DESC]
```

**CONTAINS()**: Delegates to PostgreSQL `fullTextSearch()` via `to_tsvector`/`plainto_tsquery`. Returns empty for `cmis:folder`.

**IN_TREE()**: `path LIKE folderPath/%` — all descendants at any depth (vs IN_FOLDER which matches direct children only).

---

## 4. Database Migrations

| ID | Description |
|----|-------------|
| 066 | Composite index on `replication_jobs(definition_id, status)` |
| 067 | `transfer_targets.remote_repository_id` column |
| 068 | `transfer_node_mappings` table with unique key |
| 069 | `replication_jobs.entry_report` JSONB + `report_truncated` boolean |
| 070 | `replication_definitions.last_successful_sync_at` column |
| 071 | `node_relations` table + data migration from `document_relations` |

---

## 5. Architecture Decisions

### ADR-001: Storage Routing Tenant Isolation

**Status**: Deferred

**Decision**: Retain global shared storage with hash-based deduplication. Per-tenant path/bucket routing deferred until one of four trigger conditions is met:
1. Customer requires physical data isolation (compliance/regulatory)
2. Per-tenant encryption becomes a requirement
3. Tenant deletion becomes operationally painful at scale
4. Cross-tenant dedup savings become negligible

Full ADR at `docs/adr/ADR-001-storage-routing-tenant-isolation.md`.
