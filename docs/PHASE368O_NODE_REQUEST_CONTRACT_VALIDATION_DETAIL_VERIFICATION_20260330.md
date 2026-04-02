# Phase 368O — Node Request Contract + Validation Detail — Verification

> **Date**: 2026-03-30

---

## 1. Verification Matrix

| # | Claim | Status |
|---|-------|--------|
| 1 | CreateNodeRequest has @NotBlank on name and nodeType | PASS |
| 2 | POST /nodes builds Folder when nodeType=FOLDER | PASS |
| 3 | POST /nodes builds Document when nodeType=DOCUMENT with mimeType | PASS |
| 4 | POST /nodes populates aspects from request | PASS |
| 5 | POST /nodes populates properties from request | PASS |
| 6 | POST /nodes rejects missing name with 400 | PASS |
| 7 | POST /nodes rejects missing nodeType with 400 | PASS |
| 8 | UpdateNodeRequest passes typed fields to service | PASS |
| 9 | UpdateNodeRequest omits null fields from update map | PASS |
| 10 | AddAspectRequest passes aspectName + properties to service | PASS |
| 11 | Path-style addAspect passes body as properties | PASS |
| 12 | AddAspectRequest rejects blank aspectName with 400 | PASS |
| 13 | PropertyValidationException details[] exposed in response body | PASS |
| 14 | Response has details array with correct violation strings | PASS |
| 15 | Non-validation errors (SecurityException) have no details | PASS |
| 16 | NodeService.addAspect(id, name, properties) merges props before defaults | PASS |
| 17 | Frontend api.ts consumes details[] in toast | PASS (code review) |
| 18 | Frontend types/index.ts has CreateNodeRequest/UpdateNodeRequest/AddAspectRequest/ApiErrorResponse | PASS |

## 2. Hot-File Constraint

Zero modifications to preview/rendition/search/ops-governance files.

## 3. Test Inventory

### NodeControllerRequestContractTest.java — 11 tests

```
CreateNode (4):
  ✓ creates folder from typed request
  ✓ creates document from typed request with mimeType
  ✓ rejects missing name with 400
  ✓ rejects missing nodeType with 400

UpdateNode (2):
  ✓ passes typed fields to service
  ✓ omits null fields from update map

AddAspect (3):
  ✓ body-style passes aspectName and properties to service
  ✓ path-style passes properties from body
  ✓ body-style rejects blank aspectName

ValidationDetails (2):
  ✓ response body includes details array from PropertyValidationException
  ✓ non-validation errors have no details
```

## 4. Full Regression

```
Phase 368O (Request Contract):              11 tests ✓
Phase 368M (Property Enforcement):          13 tests ✓
Phase 368K (Content Model Authoring):       53 tests ✓
Phase 361-365 (Content Model + Aspect):      6 tests ✓
Phase 364B (Lock Enhancement):              38 tests ✓
Phase 368A (Working Copy):                  54 tests ✓
──────────────────────────────────────────────────────
Total:                                     175 tests, 0 failures
BUILD SUCCESS
```

## 5. Contract Comparison

| Endpoint | Before (abstract) | After (typed) |
|----------|-------------------|---------------|
| POST /nodes | `Node` entity (JPA leak) | `CreateNodeRequest` (name, nodeType, mimeType, properties, aspects) |
| PATCH /nodes/{id} | `Map<String, Object>` (no validation) | `UpdateNodeRequest` (name, description, properties, metadata, correspondentId) |
| POST /nodes/{id}/aspects | `@RequestParam aspectName` + body ignored | `AddAspectRequest` (aspectName, properties) |
| Error response | `{ message }` only | `{ message, details[] }` with violations |
