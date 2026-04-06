# Phase 369AR — CMIS AtomPub Mutation/Conformance Sidecar

> **Scope**: Add write operations to AtomPub binding — CRUD + checkout/checkin
> **Date**: 2026-04-03

---

## 1. What Was Added

### Mutation Endpoints (7 new)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/cmis/atom/folder` | Create folder (JSON body → Atom entry response) |
| POST | `/api/cmis/atom/document` | Create document |
| PUT | `/api/cmis/atom/object` | Update properties |
| DELETE | `/api/cmis/atom/object?objectId=` | Delete object |
| POST | `/api/cmis/atom/checkout?objectId=` | Check out (creates working copy) |
| POST | `/api/cmis/atom/checkin?objectId=&keepCheckedOut=` | Check in working copy |
| POST | `/api/cmis/atom/cancel-checkout?objectId=` | Cancel checkout |

All mutation endpoints accept JSON input and return `application/atom+xml` response.

### Serializer Enhancement

`serializeMutationResponse(response, baseUrl)`:
- When response has object: produces `<atom:entry>` with full properties
- When response has no object (delete): produces `<cmisra:response>` with action/message/deletedObjectId

### Service Integration

| Operation | Delegates to |
|-----------|-------------|
| createFolder, createDocument, updateProperties, deleteObject | `CmisMutationService` (existing) |
| checkOut | `CheckOutCheckInService.checkout()` |
| checkIn | `CheckOutCheckInService.checkin()` |
| cancelCheckOut | `CheckOutCheckInService.cancelCheckout()` |

## 2. Files Modified

| File | Change |
|------|--------|
| `controller/CmisAtomPubController.java` | +CmisMutationService/CheckOutCheckInService/CmisObjectFactory deps; +7 mutation endpoints |
| `cmis/CmisAtomPubSerializer.java` | +`serializeMutationResponse()` |
| `test/cmis/CmisAtomPubSerializerTest.java` | +2 mutation serialization tests |

## 3. NOT Modified

- `CmisBrowserController.java` (hot)
- `CmisModels.java` (hot)
- `NodeService.java` (hot)
- `VersionService.java` (hot)

## 4. AtomPub Binding — Complete Endpoint Summary

| Type | Endpoints | Total |
|------|:---------:|:-----:|
| Read (369AQ) | service doc + object + children | 3 |
| **Write (369AR)** | folder + document + update + delete + checkout + checkin + cancel | **7** |
| **Total** | | **10** |
