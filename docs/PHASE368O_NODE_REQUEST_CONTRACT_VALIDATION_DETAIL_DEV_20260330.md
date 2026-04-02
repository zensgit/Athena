# Phase 368O — Node Request Contract + Validation Detail Consumption

> **Scope**: Replace abstract Node/@Map request bodies with typed DTOs; wire validation details[] to frontend toast
> **Date**: 2026-03-30

---

## 1. Problem Statement

| Gap | Risk |
|-----|------|
| `POST /nodes` accepts raw `Node` entity | Leaky abstraction — client sees JPA internals; no field-level validation messages |
| `PATCH /nodes/{id}` accepts `Map<String, Object>` | No compile-time contract; typos silently ignored |
| `POST /nodes/{id}/aspects` ignores request body properties | Frontend sends aspect properties but backend discards them |
| Frontend error toast shows only summary message | Validation violations (`details[]`) from PropertyValidationException are lost |

## 2. What Was Built

### Backend DTOs (3 new)

```java
record CreateNodeRequest(
    @NotBlank String name,
    String description,
    @NotBlank String nodeType,     // "FOLDER" or "DOCUMENT"
    String mimeType,
    Map<String, Object> properties,
    Map<String, Object> metadata,
    List<String> aspects
)

record UpdateNodeRequest(
    @Size(max = 255) String name,
    String description,
    Map<String, Object> properties,
    Map<String, Object> metadata,
    String correspondentId
)

record AddAspectRequest(
    @NotBlank String aspectName,
    Map<String, Object> properties
)
```

### Controller Changes

| Endpoint | Before | After |
|----------|--------|-------|
| `POST /nodes` | `@RequestBody Node` | `@Valid @RequestBody CreateNodeRequest` → builds Folder/Document internally |
| `PATCH /nodes/{id}` | `@RequestBody Map` | `@Valid @RequestBody UpdateNodeRequest` → builds update map from typed fields |
| `POST /nodes/{id}/aspects` (body) | `@RequestParam aspectName` | `@Valid @RequestBody AddAspectRequest` → passes properties to service |
| `POST /nodes/{id}/aspects/{name}` (path) | body ignored | `@RequestBody Map properties` → passed to service |

### Service Change

```java
// NodeService now has overloaded addAspect:
Node addAspect(UUID nodeId, String aspectName)                           // no properties
Node addAspect(UUID nodeId, String aspectName, Map<String, Object> props) // with properties
```

Properties are merged into the node **before** defaults are applied and enforcement runs.

### Frontend Changes

**api.ts error handler** — now consumes `response.data.details`:
```typescript
if (details && details.length > 0) {
  toast.error(message + '\n' + details.map(d => '• ' + d).join('\n'), {
    autoClose: 8000,
    style: { whiteSpace: 'pre-line' }
  });
}
```

**types/index.ts** — new TypeScript interfaces:
- `CreateNodeRequest`, `UpdateNodeRequest`, `AddAspectRequest`, `ApiErrorResponse`

## 3. Files Changed

### New Files

| File | Purpose |
|------|---------|
| `dto/CreateNodeRequest.java` | Typed create-node request with bean validation |
| `dto/UpdateNodeRequest.java` | Typed update-node request |
| `dto/AddAspectRequest.java` | Typed add-aspect request with properties |
| `test/controller/NodeControllerRequestContractTest.java` | 11 focused tests |

### Modified Files

| File | Change |
|------|--------|
| `NodeController.java` | createNode/updateNode/addAspect endpoints refactored to use DTOs |
| `NodeService.java` | +overloaded `addAspect(id, name, properties)` |
| `api.ts` | Error handler consumes `details[]` from responses |
| `types/index.ts` | +CreateNodeRequest, UpdateNodeRequest, AddAspectRequest, ApiErrorResponse |

### NOT Modified

All preview/rendition/search/ops-governance files untouched.

## 4. Validation Detail Flow

```
Client sends:
  POST /nodes/{id}/aspects
  { "aspectName": "cm:titled" }
                    ↓
NodeController.addAspectByBody()
  → NodeService.addAspect(id, "cm:titled", null)
    → applyAspectDefaults()  // no default for cm:title
    → enforceAspectProperties()
      → "Missing mandatory property 'cm:title' for aspect cm:titled"
    → throw PropertyValidationException(message, violations)
                    ↓
RestExceptionHandler.handlePropertyValidation()
  → HTTP 400 { message: "...", details: ["Missing mandatory..."] }
                    ↓
Frontend api.ts interceptor
  → toast.error("Property validation failed\n• Missing mandatory property 'cm:title'...")
```

## 5. Backward Compatibility

- Path-style `POST /nodes/{id}/aspects/{name}` still works (body optional)
- `PATCH /nodes/{id}` accepts same field names as before (name, description, properties, metadata, correspondentId)
- Null fields in UpdateNodeRequest are omitted from the update map (no overwrite)
- Frontend `addAspect(nodeId, aspect, properties?)` signature unchanged
