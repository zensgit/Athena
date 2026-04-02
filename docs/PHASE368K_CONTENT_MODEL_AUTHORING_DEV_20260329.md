# Phase 368K — Content Model Authoring

> **Scope**: Duplicate-name validation, CRUD for sub-entities, frontend authoring dialogs, DictionaryService tests
> **Date**: 2026-03-29
> **Backlog ref**: `ATHENA_SURPASS_ALFRESCO_DEVELOPMENT_PLAN_20260329.md` — Content Model hardening pass

---

## 1. Problem Statement

Phase 361-365 delivered the content model skeleton (entities, repositories, services, DTOs,
controllers, frontend page). But several gaps remained:

| Gap | Risk |
|-----|------|
| No duplicate type/aspect/property name validation | Silent data corruption — two types with same name in one model |
| No update/delete for types, aspects, properties, constraints | Users cannot edit or remove definitions after creation |
| No frontend authoring UI for sub-entities | Admin page is read-only inspect + create model; cannot author types/aspects/properties |
| No DictionaryService tests | Inheritance resolution, hierarchy traversal, and QName parsing untested |
| Frontend service missing CRUD methods | Only model-level CRUD exposed in contentModelService.ts |

## 2. What Was Built

### Backend Validation (ContentModelService)

| Guard | Before | After |
|-------|--------|-------|
| Duplicate type name within model | None | `IllegalArgumentException` on `addType()` |
| Duplicate aspect name within model | None | `IllegalArgumentException` on `addAspectDefinition()` |
| Duplicate property name on type | None | `IllegalArgumentException` on `addProperty(id, prop, false)` |
| Duplicate property name on aspect | None | `IllegalArgumentException` on `addProperty(id, prop, true)` |

### Backend CRUD for Sub-entities

| Method | Description |
|--------|-------------|
| `updateType(typeId, title, description, parentName)` | Update type fields |
| `deleteType(typeId)` | Hard-delete type + cascaded properties |
| `updateAspect(aspectId, title, description, parentName)` | Update aspect fields |
| `deleteAspect(aspectId)` | Hard-delete aspect + cascaded properties |
| `deleteProperty(propertyId)` | Hard-delete property + cascaded constraints |
| `deleteConstraint(constraintId)` | Hard-delete constraint |

### New Endpoints (8)

| Method | Path | Description |
|--------|------|-------------|
| PUT | `/api/content-models/types/{typeId}` | Update type |
| DELETE | `/api/content-models/types/{typeId}` | Delete type |
| PUT | `/api/content-models/aspects/{aspectId}` | Update aspect |
| DELETE | `/api/content-models/aspects/{aspectId}` | Delete aspect |
| DELETE | `/api/content-models/properties/{propertyId}` | Delete property |
| DELETE | `/api/content-models/constraints/{constraintId}` | Delete constraint |

### Frontend Authoring UI

Three new dialogs added to `ContentModelsPage.tsx`:

1. **Add Type Dialog** — name (required), title, description, parent type (qualified name)
2. **Add Aspect Dialog** — name (required), title, description, parent aspect
3. **Add Property Dialog** — name (required), title, data type dropdown (15 types), mandatory toggle, multi-valued toggle, default value

Entry points:
- "Add Type" / "Add Aspect" buttons in the Selected Model panel
- "+" icon on each type/aspect chip → opens Add Property dialog targeted to that type/aspect

### Frontend Service Methods (9 new)

```typescript
addType(modelId, { name, title?, description?, parentName? })
updateType(typeId, { title?, description?, parentName? })
deleteType(typeId)
addAspect(modelId, { name, title?, description?, parentName? })
updateAspect(aspectId, { title?, description?, parentName? })
deleteAspect(aspectId)
addPropertyToType(typeId, { name, title?, dataType, mandatory?, multiValued?, defaultValue? })
addPropertyToAspect(aspectId, { ... })
deleteProperty(propertyId)
addConstraint(propertyId, { constraintType, parameters })
deleteConstraint(constraintId)
```

## 3. Files Changed

### New Test Files

| File | Tests |
|------|:-----:|
| `ContentModelValidationTest.java` | 9 |
| `DictionaryServiceTest.java` | 14 |

### Modified Files

| File | Change |
|------|--------|
| `ContentModelService.java` | +duplicate-name guards on addType/addAspect/addProperty; +updateType, deleteType, updateAspect, deleteAspect, deleteProperty, deleteConstraint |
| `ContentModelController.java` | +8 new PUT/DELETE endpoints |
| `contentModelService.ts` | +11 new CRUD methods for types/aspects/properties/constraints |
| `ContentModelsPage.tsx` | +3 authoring dialogs (Add Type, Add Aspect, Add Property); +handler functions; +"Add" buttons on model detail panel |

### NOT Modified

All files in preview/rendition/search/ops-governance packages untouched.

## 4. Test Inventory (23 new tests)

### ContentModelValidationTest.java — 9 tests

```
DuplicateType (2):
  ✓ rejects duplicate type name within model
  ✓ allows different type names

DuplicateAspect (1):
  ✓ rejects duplicate aspect name within model

DuplicateProperty (2):
  ✓ rejects duplicate property on type
  ✓ rejects duplicate property on aspect

UpdateDelete (4):
  ✓ updateType changes fields
  ✓ deleteType calls repo
  ✓ deleteProperty calls repo
  ✓ deleteConstraint calls repo
```

### DictionaryServiceTest.java — 14 tests

```
ParseQName (5):
  ✓ parses valid qualified name
  ✓ rejects blank input
  ✓ rejects no colon
  ✓ rejects leading colon
  ✓ rejects trailing colon

GetType (2):
  ✓ returns type by qualified name
  ✓ throws when type not found

GetAspect (1):
  ✓ returns aspect by qualified name

TypePropertyInheritance (3):
  ✓ resolves single-level properties
  ✓ merges parent properties
  ✓ child property overrides parent

TypeHierarchy (1):
  ✓ returns root-to-leaf order

MandatoryAspects (2):
  ✓ returns mandatory aspects list
  ✓ returns empty when no mandatory aspects
```
