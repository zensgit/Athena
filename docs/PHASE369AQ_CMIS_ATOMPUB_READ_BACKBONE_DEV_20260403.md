# Phase 369AQ — CMIS AtomPub Read Backbone

> **Scope**: Read-only AtomPub binding for CMIS 1.1 — service document, object entry, children feed
> **Date**: 2026-04-03

---

## 1. What Was Built

### CmisAtomPubSerializer

Converts CmisModels objects to Atom Publishing Protocol XML:

| Method | Output |
|--------|--------|
| `serializeServiceDocument(info, baseUrl)` | `application/atomsvc+xml` — workspace with repositoryInfo + root collection |
| `serializeObjectEntry(entry, baseUrl)` | `application/atom+xml` entry — properties, self/up/down links |
| `serializeChildrenFeed(children, baseUrl)` | `application/atom+xml` feed — entries + numItems + hasMoreItems |

XML escaping for `&`, `<`, `>`, `"` in all text values.

Folder entries include `rel="down"` link to children feed. Document entries do not.

### CmisAtomPubController

Read-only endpoints producing Atom XML:

| Method | Path | Content-Type | Description |
|--------|------|:------------:|-------------|
| GET | `/api/cmis/atom` | `application/atomsvc+xml` | Service document |
| GET | `/api/cmis/atom/object` | `application/atom+xml` | Object entry (by objectId or path) |
| GET | `/api/cmis/atom/children` | `application/atom+xml` | Children feed (paginated) |

Reuses `CmisBrowserService` for all data access — no duplication of node resolution logic.

### Architecture

```
Client (CMIS AtomPub consumer)
  ↓ GET /api/cmis/atom/...
CmisAtomPubController
  ↓ delegates to
CmisBrowserService (existing — NOT modified)
  ↓ returns CmisModels.*
CmisAtomPubSerializer (NEW)
  ↓ serializes to Atom XML
Client receives application/atom+xml
```

## 2. Files Created

| File | Purpose |
|------|---------|
| `cmis/CmisAtomPubSerializer.java` | Atom XML serialization |
| `controller/CmisAtomPubController.java` | AtomPub read-only endpoints |
| `test/cmis/CmisAtomPubSerializerTest.java` | 5 focused tests |

## 3. NOT Modified

- `CmisBrowserController.java` (hot)
- `CmisModels.java` (hot)
- `NodeService.java` (hot)
- `VersionService.java` (hot)
- All preview/rendition/search/ops-governance files
