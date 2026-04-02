# Phase 361-365 — Content Model / Data Dictionary + Aspect System

> **Scope**: Custom type/aspect/property/constraint definitions + runtime node aspect attachment
> **Date**: 2026-03-29
> **Backlog ref**: `ATHENA_SURPASS_ALFRESCO_DEVELOPMENT_PLAN_20260329.md` → Sprint 1 Lines A+B

---

## 1. Problem Statement

Alfresco's content model (Data Dictionary) is its most foundational subsystem — it defines custom types, aspects, properties, and constraints that govern how content is created, validated, and queried. Athena had no equivalent; types/aspects were hardcoded strings.

## 2. What Was Built

### Track A: Content Model / Data Dictionary

**5 new entities** defining the model hierarchy:

```
ContentModelDefinition (prefix, namespace, status)
  ├── TypeDefinition (name, parentName, mandatoryAspects)
  │     └── PropertyDefinition (name, dataType, mandatory, multiValued, defaultValue)
  │           └── ConstraintDefinition (REGEX | LIST | RANGE | LENGTH, parameters JSONB)
  └── AspectDefinition (name, parentName)
        └── PropertyDefinition → ConstraintDefinition
```

**3 new enums**: `PropertyDataType` (15 types), `ConstraintType` (4 types), `ModelStatus` (DRAFT/ACTIVE/DISABLED)

**5 new repositories** with qualified-name lookup and active-model filtering

**2 new services**:
- `ContentModelService` — model CRUD, activate/deactivate lifecycle, type/aspect/property/constraint composition
- `DictionaryService` — read-only queries: type/aspect lookup by qualified name, property resolution with parent inheritance, type hierarchy traversal, mandatory aspect resolution

**1 new validator**: `PropertyConstraintValidator` — executes REGEX/LIST/RANGE/LENGTH constraints against property values

**2 new controllers** (20 endpoints total):
- `ContentModelController` — 12 endpoints for model management
- `DictionaryController` — 8 endpoints for dictionary queries

### Track B: Node Aspect System

**Node.java** enhanced with:
- `Set<String> aspects` — `@ElementCollection` stored in `node_aspects` join table
- `hasAspect()`, `addAspect()`, `removeAspect()` helper methods

**NodeService** enhanced with:
- `getAspects(nodeId)`, `addAspect(nodeId, name)`, `removeAspect(nodeId, name)`, `hasAspect(nodeId, name)`
- `removeAspect` cleans prefixed properties from JSONB

**NodeController** enhanced with 3 new endpoints:
- `GET /nodes/{id}/aspects` — list attached aspects
- `POST /nodes/{id}/aspects?aspectName=cm:titled` — attach aspect
- `DELETE /nodes/{id}/aspects/{name}` — detach aspect

**NodeDto** updated to populate `aspects` from the persisted `node_aspects` table

## 3. New Files (19)

| Category | File |
|----------|------|
| Enums | `PropertyDataType.java`, `ConstraintType.java`, `ModelStatus.java` |
| Entities | `ContentModelDefinition.java`, `TypeDefinition.java`, `AspectDefinition.java`, `PropertyDefinition.java`, `ConstraintDefinition.java` |
| Repositories | `ContentModelDefinitionRepository.java`, `TypeDefinitionRepository.java`, `AspectDefinitionRepository.java`, `PropertyDefinitionRepository.java`, `ConstraintDefinitionRepository.java` |
| Services | `ContentModelService.java`, `DictionaryService.java`, `PropertyConstraintValidator.java` |
| Controllers | `ContentModelController.java`, `DictionaryController.java` |
| Migrations | `040-create-content-model-tables.xml`, `041-create-node-aspects-table.xml` |

## 4. Modified Files (4)

| File | Change |
|------|--------|
| `Node.java` | +`aspects` ElementCollection, +`hasAspect/addAspect/removeAspect` |
| `NodeDto.java` | Populates aspects from `node.getAspects()` |
| `NodeService.java` | +4 aspect management methods |
| `NodeController.java` | +3 aspect endpoints |
| `db.changelog-master.xml` | +040, +041 |

## 5. New Endpoints

### Content Model Controller (12)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/content-models` | List all models |
| GET | `/api/content-models/{id}` | Get model by ID |
| POST | `/api/content-models` | Create model |
| PUT | `/api/content-models/{id}` | Update name/description |
| POST | `/api/content-models/{id}/activate` | Activate model |
| POST | `/api/content-models/{id}/deactivate` | Deactivate model |
| DELETE | `/api/content-models/{id}` | Soft-delete model |
| POST | `/api/content-models/{id}/types` | Add type to model |
| POST | `/api/content-models/{id}/aspects` | Add aspect to model |
| POST | `/api/content-models/types/{id}/properties` | Add property to type |
| POST | `/api/content-models/aspects/{id}/properties` | Add property to aspect |
| POST | `/api/content-models/properties/{id}/constraints` | Add constraint |

### Dictionary Controller (8)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/dictionary/types` | List active types |
| GET | `/api/dictionary/types/{qname}` | Get type by qualified name |
| GET | `/api/dictionary/types/{qname}/properties` | Properties with inheritance |
| GET | `/api/dictionary/types/{qname}/hierarchy` | Type parent chain |
| GET | `/api/dictionary/types/{qname}/mandatory-aspects` | Mandatory aspects |
| GET | `/api/dictionary/aspects` | List active aspects |
| GET | `/api/dictionary/aspects/{qname}` | Get aspect |
| GET | `/api/dictionary/aspects/{qname}/properties` | Aspect properties |

### Node Aspect Endpoints (3)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/nodes/{id}/aspects` | List node aspects |
| POST | `/api/nodes/{id}/aspects` | Attach aspect |
| DELETE | `/api/nodes/{id}/aspects/{name}` | Detach aspect |

## 6. Database Schema

### Migration 040: Content Model Tables (5 tables)

```
content_model_definitions (id, namespace_uri, prefix, name, description, author, status, version_label, ...)
type_definitions         (id, name, title, description, parent_name, model_id FK, mandatory_aspects JSONB, ...)
aspect_definitions       (id, name, title, description, parent_name, model_id FK, ...)
property_definitions     (id, name, title, description, data_type, mandatory, multi_valued, default_value,
                          indexed, protected_field, type_definition_id FK, aspect_definition_id FK, ...)
constraint_definitions   (id, constraint_type, parameters JSONB, property_definition_id FK, ...)
```

### Migration 041: Node Aspects Table

```
node_aspects (node_id FK, aspect_name) — composite PK
```

## 7. Constraint Validation

```java
PropertyConstraintValidator.validate(value, List<ConstraintDefinition>) → List<String> violations

REGEX:  params.expression → Pattern.matches()
LIST:   params.allowedValues → List.contains()
RANGE:  params.minValue/maxValue → double comparison
LENGTH: params.minLength/maxLength → string length check
```

## 8. Backward Compatibility

- `NodeDto.aspects` field was always present (previously hardcoded `cm:versionable`); now populated from DB
- Existing nodes without aspects → empty set, no behavior change
- All new endpoints are additive
- No hot files (preview/search/rendition) modified
