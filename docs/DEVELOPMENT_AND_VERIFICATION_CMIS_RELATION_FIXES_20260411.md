# Development And Verification: CMIS / Relation Hardening

Date: 2026-04-11

## Scope

This batch closed four concrete defects found during CMIS and relation-layer review:

1. `DocumentRelationService` and CMIS relationships were writing/reading different tables (`document_relations` vs `node_relations`), causing split-brain.
2. CMIS version-history endpoints returned version-specific object IDs (`uuid;v1.0`) that other CMIS selectors/actions could not consume.
3. CMIS change log pagination was not stable across same-timestamp audit rows and had no tenant/ACL visibility filter.
4. CMIS ACL round-trip lost authority type and compressed `cmis:all` into an incomplete Athena permission set.

## Implemented Changes

### 1. Relation-source unification

- Added legacy-service delegation onto `node_relations`:
  - `ecm-core/src/main/java/com/ecm/core/service/DocumentRelationService.java`
  - `ecm-core/src/main/java/com/ecm/core/service/NodeRelationService.java`
- `DocumentRelationService` now reads/writes through `NodeRelationService` and converts `NodeRelation -> DocumentRelation` for legacy API compatibility.
- This removes the old split where CMIS wrote `node_relations` while legacy REST kept reading `document_relations`.

### 2. Version-specific CMIS objectId support

- Added a shared parser:
  - `ecm-core/src/main/java/com/ecm/core/cmis/CmisObjectReference.java`
- Integrated parser into:
  - `CmisBrowserService`
  - `CmisContentVersioningService`
  - `CmisMutationService`
  - `CmisRelationshipService`
  - `CmisAclService`
- `getObject(uuid;vX.Y)` now resolves to the matching version entry.
- `content(uuid;vX.Y)` now resolves to frozen version content.
- ACL, relationships, and mutations now accept version-specific IDs by resolving them to the live version series node.

### 3. CMIS change log hardening

- `CmisChangeLogService` now:
  - uses stable cursor tokens: `eventTime|auditLogId`
  - pages by `eventTime ASC, id ASC`
  - preserves backward compatibility for old timestamp-only tokens
  - filters entries through tenant path visibility and `READ` permission
  - keeps caller token stable when no new events are returned
- Repository support added in:
  - `ecm-core/src/main/java/com/ecm/core/repository/AuditLogRepository.java`

### 4. ACL mapping hardening

- `cmis:all` now expands back to the full Athena admin-like permission set:
  - `DELETE`
  - `DELETE_CHILDREN`
  - `CHANGE_PERMISSIONS`
  - `TAKE_OWNERSHIP`
  - `EXECUTE`
  - `APPROVE`
  - `REJECT`
- `cmis:write` now round-trips to:
  - `WRITE`
  - `CREATE_CHILDREN`
  - `CHECKOUT`
  - `CHECKIN`
  - `CANCEL_CHECKOUT`
- ACL apply now preserves authority type heuristically for:
  - `EVERYONE`
  - `ROLE_*` / persisted roles
  - persisted groups
  - default user principals

## Files Changed

### Main code

- `ecm-core/src/main/java/com/ecm/core/cmis/CmisAclService.java`
- `ecm-core/src/main/java/com/ecm/core/cmis/CmisBrowserService.java`
- `ecm-core/src/main/java/com/ecm/core/cmis/CmisChangeLogService.java`
- `ecm-core/src/main/java/com/ecm/core/cmis/CmisContentVersioningService.java`
- `ecm-core/src/main/java/com/ecm/core/cmis/CmisMutationService.java`
- `ecm-core/src/main/java/com/ecm/core/cmis/CmisRelationshipService.java`
- `ecm-core/src/main/java/com/ecm/core/cmis/CmisObjectReference.java`
- `ecm-core/src/main/java/com/ecm/core/repository/AuditLogRepository.java`
- `ecm-core/src/main/java/com/ecm/core/service/DocumentRelationService.java`
- `ecm-core/src/main/java/com/ecm/core/service/NodeRelationService.java`

### Tests

- `ecm-core/src/test/java/com/ecm/core/cmis/CmisAclServiceTest.java`
- `ecm-core/src/test/java/com/ecm/core/cmis/CmisChangeLogServiceTest.java`
- `ecm-core/src/test/java/com/ecm/core/cmis/CmisContentVersioningServiceTest.java`
- `ecm-core/src/test/java/com/ecm/core/cmis/CmisRelationshipServiceTest.java`
- `ecm-core/src/test/java/com/ecm/core/cmis/CmisVersionHistoryTest.java`
- `ecm-core/src/test/java/com/ecm/core/service/DocumentRelationAssociationTest.java`

## Verification

### Compile

Command:

```bash
cd ecm-core
mvn -q -DskipTests compile
```

Result:

- Passed

### Focused service tests

Command:

```bash
cd ecm-core
mvn -q -Dtest=CmisChangeLogServiceTest,CmisAclServiceTest,CmisVersionHistoryTest,CmisContentVersioningServiceTest,CmisRelationshipServiceTest,DocumentRelationAssociationTest test
```

Result:

- Passed

### Controller / interop tests

Command:

```bash
cd ecm-core
mvn -q -Dtest=CmisBrowserControllerTest,CmisInteropSmokePackTest test
```

Result:

- Passed

### Diff hygiene

Command:

```bash
git diff --check
```

Result:

- Passed

## Notes And Known Boundaries

- `DocumentRelationService` intentionally remains document-only at the API surface. Mixed folder/document relationships created through CMIS remain visible through CMIS and `NodeRelationService`, but are not surfaced as legacy `DocumentRelation` objects.
- Old timestamp-only change-log tokens remain backward compatible, but same-timestamp stability is guaranteed only after the client receives and uses the new `eventTime|auditLogId` cursor format.
- A read-only Claude CLI review was used only as a secondary reviewer for residual-risk spotting; repository changes were implemented and verified locally.
