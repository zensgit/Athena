# Phase 368Q — Type Property Enforcement

> **Scope**: Wire dictionary type mandatory/default/constraint enforcement + mandatory-aspect auto-attach into NodeService create/update
> **Date**: 2026-03-30

---

## 1. Problem Statement

Phase 368M wired **aspect** property enforcement into NodeService. But the content-model
**type** side remained disconnected — a node with `typeQName = "cm:content"` was
not validated against the type definition's mandatory properties or constraints,
and mandatory aspects declared on the type were not auto-attached.

| Gap | After |
|-----|-------|
| Node has no content-model type reference | +`typeQName` field on Node entity |
| Type mandatory/default properties not enforced | +`enforceTypeProperties()` — defaults → mandatory → constraints |
| Type mandatory aspects not auto-attached | +`applyMandatoryAspects()` — auto-adds aspects + their defaults |
| createNode doesn't enforce type | Now calls: `applyMandatoryAspects` → `enforceTypeProperties` → `enforceAspectProperties` |
| updateNode doesn't enforce type | Now calls: `enforceTypeProperties` → `enforceAspectProperties` |
| CreateNodeRequest lacks typeQName | +`typeQName` field |

## 2. Implementation

### Entity: Node.java
```java
@Column(name = "type_qname", length = 200)
private String typeQName;   // e.g. "cm:content", "inv:invoice"
```

### NodeService: 3 new internal methods

```java
void enforceTypeProperties(Node node)
  — if typeQName is null/blank → skip (untyped node)
  — lookup PropertyDefinitions from dictionaryService.getPropertiesForType()
  — apply defaults for missing properties
  — validate mandatory + constraints
  — throw PropertyValidationException with all violations

void applyMandatoryAspects(Node node)
  — if typeQName is null/blank → skip
  — lookup mandatory aspects from dictionaryService.getMandatoryAspectsForType()
  — for each: if not already attached → addAspect + applyAspectDefaults

(existing) void enforceAspectProperties(Node node) — unchanged
```

### Enforcement order in createNode
```
createNode(node, parentId)
  ├── permission checks, duplicate name check
  ├── applyMandatoryAspects(node)      ← NEW: auto-attach type's mandatory aspects
  ├── enforceTypeProperties(node)       ← NEW: type defaults → mandatory → constraints
  ├── enforceAspectProperties(node)     ← existing: aspect defaults → mandatory → constraints
  └── nodeRepository.save(node)
```

### Enforcement order in updateNode
```
updateNode(nodeId, updates)
  ├── property merge
  ├── enforceTypeProperties(node)       ← NEW
  ├── enforceAspectProperties(node)     ← existing
  └── nodeRepository.save(node)
```

## 3. Files Changed

### New Files

| File | Purpose |
|------|---------|
| `db/changelog/changes/042-add-node-type-qname-column.xml` | Migration: `type_qname varchar(200)` + index |
| `test/service/NodeServiceTypeEnforcementTest.java` | 14 focused tests |

### Modified Files

| File | Change |
|------|--------|
| `entity/Node.java` | +`typeQName` field |
| `dto/NodeDto.java` | +`typeQName` in record, `from()`, `withPreviewSemantics()` |
| `dto/CreateNodeRequest.java` | +`typeQName` field |
| `controller/NodeController.java` | createNode sets `typeQName` from request |
| `service/NodeService.java` | +`enforceTypeProperties()`, +`applyMandatoryAspects()`, wired into createNode/updateNode |
| `db/changelog/db.changelog-master.xml` | +042 |
| `types/index.ts` | +`typeQName` on Node, CreateNodeRequest |

### NOT Modified

All preview/rendition/search/ops-governance files untouched.

## 4. Test Inventory — 14 tests

```
TypeDefaults (2):
  ✓ applies default values from type definition
  ✓ does not overwrite existing property with default

TypeMandatory (3):
  ✓ rejects missing mandatory type property
  ✓ passes when mandatory property has default applied
  ✓ passes when mandatory property is provided by caller

TypeConstraints (2):
  ✓ rejects value failing LIST constraint on type property
  ✓ passes valid constraint

MandatoryAspects (2):
  ✓ auto-attaches mandatory aspects declared on type
  ✓ does not duplicate already-attached aspects

Unmanaged (2):
  ✓ skips enforcement when no typeQName set
  ✓ skips enforcement for unregistered type

CreateNodeWiring (2):
  ✓ createNode enforces type properties before save
  ✓ createNode auto-attaches mandatory aspects and enforces

UpdateNodeWiring (1):
  ✓ updateNode enforces type constraints after property merge
```
