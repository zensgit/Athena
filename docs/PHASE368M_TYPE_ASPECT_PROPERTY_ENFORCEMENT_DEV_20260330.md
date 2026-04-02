# Phase 368M — Type/Aspect Property Enforcement

> **Scope**: Wire dictionary mandatory/default/constraint enforcement into NodeService create/update/addAspect
> **Date**: 2026-03-30
> **Backlog ref**: Content Model hardening — enforcement pass

---

## 1. Problem Statement

Phase 361-365 and 368K delivered the content model infrastructure (entities, DTOs,
validation, authoring UI) but the **runtime enforcement** was never connected —
`NodeService.createNode()`, `updateNode()`, and `addAspect()` operated without
checking mandatory properties, default values, or constraint rules from the
active dictionary.

| Operation | Before | After |
|-----------|--------|-------|
| `createNode` with aspect | No validation | Enforces mandatory + constraints |
| `updateNode` properties | Raw map merge | Enforces mandatory + constraints |
| `addAspect` | Just adds name to set | Applies defaults → enforces mandatory + constraints |

## 2. Design Decisions

| # | Decision | Rationale |
|---|----------|-----------|
| 1 | DictionaryService + PropertyConstraintValidator injected via `@Autowired @Lazy` | Avoids constructor bloat and circular dependency risk with RuleEngineService |
| 2 | Enforcement is **gracefully optional** — if injected beans are null, skip | Backward-compatible; existing tests without mocks still pass |
| 3 | Unmanaged aspects (no definition registered) silently skipped | Allows attaching ad-hoc aspects without requiring a content model |
| 4 | `applyAspectDefaults` runs **before** `enforceAspectProperties` | Defaults satisfy mandatory checks — no false rejects |
| 5 | Violations are collected and thrown as a single `IllegalArgumentException` | One clear error message with all violations listed |

## 3. Implementation

### New Methods on NodeService

```java
/** Validate node properties against all attached aspect definitions. */
void enforceAspectProperties(Node node)
  - Iterates all aspects on the node
  - For each: looks up PropertyDefinitions from DictionaryService
  - Checks mandatory properties (blank/null = violation)
  - Runs PropertyConstraintValidator on non-null values
  - Collects all violations, throws single IllegalArgumentException

/** Apply default property values from an aspect definition. */
void applyAspectDefaults(Node node, String aspectName)
  - Looks up PropertyDefinitions from DictionaryService
  - For each: if property not already set and defaultValue != null, sets it
```

### Wiring Points

| Method | Enforcement |
|--------|------------|
| `createNode()` | `enforceAspectProperties(node)` — after duplicate name check, before save |
| `updateNode()` | `enforceAspectProperties(node)` — after property merge, before save |
| `addAspect()` | `applyAspectDefaults(node, aspectName)` then `enforceAspectProperties(node)` — before save |

### Injection

```java
@Autowired @Lazy
private DictionaryService dictionaryService;

@Autowired @Lazy
private PropertyConstraintValidator propertyConstraintValidator;
```

## 4. Files Changed

| File | Change |
|------|--------|
| `NodeService.java` | +2 `@Autowired @Lazy` fields; +`enforceAspectProperties()`; +`applyAspectDefaults()`; enforcement calls in createNode, updateNode, addAspect |
| `NodeServicePropertyEnforcementTest.java` | **NEW** — 13 focused tests |

### NOT Modified

All preview/rendition/search/ops-governance files untouched.

## 5. Enforcement Flow

```
addAspect("cm:titled")
  ├── node.addAspect("cm:titled")
  ├── applyAspectDefaults(node, "cm:titled")
  │     └── for each PropertyDefinition:
  │           if property not set AND defaultValue != null → set it
  ├── enforceAspectProperties(node)
  │     └── for each aspect on node:
  │           for each PropertyDefinition:
  │             ├── if mandatory AND (null or blank) → violation
  │             └── if value != null AND constraints → validate(value, constraints)
  │           collect all violations
  │           if any → throw IllegalArgumentException
  └── nodeRepository.save(node)
```

## 6. Error Response

```json
HTTP 400 Bad Request

{
  "error": "Property validation failed: Missing mandatory property 'cm:title' for aspect cm:titled; Value 'URGENT' is not in the allowed list [LOW, MEDIUM, HIGH]"
}
```

## 7. Backward Compatibility

- Nodes without aspects: `enforceAspectProperties` returns immediately (no-op)
- Nodes with unmanaged aspects (no definition): silently skipped
- Existing tests without DictionaryService mock: fields remain null → enforcement skipped
- No hot files modified
