# Phase 368R — Node Associations Enhancement

> **Scope**: Peer/secondary-child/assocType association model, DB migration, endpoints, frontend service
> **Date**: 2026-03-30

---

## 1. Problem Statement

Athena had a `DocumentRelation` entity with `source → target` directed edges and
a free-form `relationType` string, but:

- **No DB migration** — the `document_relations` table was never created
- **No association direction model** — no peer vs secondary-child distinction
- **No assocType** — no qualified association type (e.g. `cm:references`)
- **No secondary-child endpoints** — no multi-filing support
- **No peer association endpoints** — only generic `/relations/targets` and `/relations/sources` existed

Alfresco provides:
- Peer associations (bidirectional references between nodes)
- Secondary children (a node can appear in multiple parent folders)
- Typed associations via qualified names (QNames)

## 2. What Was Built

### AssocDirection enum
```java
PEER              // symmetric peer association
CHILD_PRIMARY     // filesystem hierarchy (existing parent→child)
CHILD_SECONDARY   // multi-filing secondary parent→child
```

### Entity Enhancement (DocumentRelation)
```java
+String assocType        // qualified name (e.g. "cm:references", "app:related")
+AssocDirection direction // PEER | CHILD_PRIMARY | CHILD_SECONDARY
+Integer orderIndex       // for ordering associations
```

### DB Migration 043
Creates `document_relations` table (was missing!) with all columns + indexes.

### Service Enhancement (DocumentRelationService)

| Method | Description |
|--------|-------------|
| `createPeerAssociation(srcId, tgtId, assocType)` | Create peer association with direction=PEER |
| `getTargetAssociations(nodeId, assocType?)` | Outgoing peer associations (filter by assocType) |
| `getSourceAssociations(nodeId, assocType?)` | Incoming peer associations |
| `removePeerAssociation(srcId, tgtId)` | Remove peer association |
| `addSecondaryChild(parentId, childId)` | Add secondary child with direction=CHILD_SECONDARY |
| `removeSecondaryChild(parentId, childId)` | Remove secondary child |
| `getSecondaryChildren(parentId)` | List secondary children |
| `getSecondaryParents(childId)` | List secondary parents |

### New Endpoints (8)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/nodes/{id}/targets?assocType=` | List target peer associations |
| POST | `/nodes/{id}/targets?targetId=&assocType=` | Create peer association |
| DELETE | `/nodes/{id}/targets/{targetId}` | Remove peer association |
| GET | `/nodes/{id}/sources?assocType=` | List source peer associations |
| POST | `/nodes/{id}/secondary-children?childId=` | Add secondary child |
| GET | `/nodes/{id}/secondary-children` | List secondary children |
| DELETE | `/nodes/{id}/secondary-children/{childId}` | Remove secondary child |
| GET | `/nodes/{id}/secondary-parents` | List secondary parents |

### Frontend Service Methods (9 new)

```typescript
getTargetAssociations(nodeId, assocType?)
createTargetAssociation(nodeId, targetId, assocType)
removeTargetAssociation(nodeId, targetId)
getSourceAssociations(nodeId, assocType?)
addSecondaryChild(parentId, childId)
removeSecondaryChild(parentId, childId)
getSecondaryChildren(nodeId)
getSecondaryParents(nodeId)
```

## 3. Files Changed

### New Files
| File | Purpose |
|------|---------|
| `entity/AssocDirection.java` | PEER, CHILD_PRIMARY, CHILD_SECONDARY enum |
| `db/changelog/changes/043-create-document-relations-table.xml` | Full table creation + indexes |
| `test/service/DocumentRelationAssociationTest.java` | 10 focused tests |

### Modified Files
| File | Change |
|------|--------|
| `entity/DocumentRelation.java` | +assocType, +direction, +orderIndex fields; +indexes |
| `repository/DocumentRelationRepository.java` | +direction-based queries, +deleteBySourceAndTarget |
| `service/DocumentRelationService.java` | +8 new methods for peer/secondary-child CRUD |
| `controller/NodeController.java` | +8 new endpoints + toEdgeDto helper |
| `services/nodeService.ts` | +9 frontend service methods |
| `db/changelog/db.changelog-master.xml` | +043 |

### NOT Modified
All preview/rendition/search/ops-governance files untouched.
