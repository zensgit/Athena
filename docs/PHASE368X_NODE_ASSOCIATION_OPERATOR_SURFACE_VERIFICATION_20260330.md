# Phase 368X — Node Association Operator Surface — Verification

> **Date**: 2026-03-30

---

## 1. Verification Matrix

| # | Claim | Status |
|---|-------|--------|
| 1 | AssociationManager dialog component exists | PASS |
| 2 | 4 tabs: Targets, Sources, Sec. Children, Sec. Parents | PASS |
| 3 | Targets tab: add form (node ID + assocType) | PASS |
| 4 | Targets tab: remove button per row | PASS |
| 5 | Sources tab: read-only (no delete) | PASS |
| 6 | Sec. Children tab: add form (child ID) | PASS |
| 7 | Sec. Children tab: remove button per row | PASS |
| 8 | Sec. Parents tab: read-only (no delete) | PASS |
| 9 | Open-in-browser link per row → `/browse/:nodeId` | PASS |
| 10 | Tab counts show association counts | PASS |
| 11 | uiSlice has associationManagerOpen state + action | PASS |
| 12 | MainLayout renders AssociationManager | PASS |
| 13 | FileList context menu has "Associations" item | PASS |
| 14 | Context menu dispatches to Redux | PASS |
| 15 | GET /targets returns edge DTOs with source/target names | PASS |
| 16 | GET /targets filters by assocType param | PASS |
| 17 | POST /targets creates peer and returns 201 | PASS |
| 18 | DELETE /targets/{id} removes and returns 204 | PASS |
| 19 | POST /secondary-children adds and returns 201 | PASS |
| 20 | GET /secondary-children returns edges | PASS |
| 21 | DELETE /secondary-children/{id} removes and returns 204 | PASS |

## 2. Hot-File Constraint

Zero modifications to preview/rendition/search/ops-governance files.

## 3. Test Inventory

### NodeControllerAssociationEndpointTest.java — 7 tests

```
GetTargets (2):
  ✓ returns peer target associations as edge DTOs
  ✓ filters by assocType parameter

CreateTarget (1):
  ✓ creates peer association and returns 201

RemoveTarget (1):
  ✓ removes peer association and returns 204

AddSecondaryChild (1):
  ✓ adds secondary child and returns 201

GetSecondaryChildren (1):
  ✓ returns secondary children as edge DTOs

RemoveSecondaryChild (1):
  ✓ removes secondary child and returns 204
```

## 4. Full Regression

```
Phase 368X (Association Operator Surface):    7 tests ✓
Phase 368W (Cross-Surface Entry):             4 tests ✓
Phase 368V (Admin Governance Surface):       10 tests ✓
Phase 368U (Operator Surface Convergence):    4 tests ✓
Phase 368T (Shared Links Enhancement):        9 tests ✓
Phase 368R (Node Associations):              10 tests ✓
Phase 368Q (Type Enforcement):               14 tests ✓
Phase 368O (Request Contract):               11 tests ✓
Phase 368M (Aspect Property Enforcement):    13 tests ✓
Phase 368K (Content Model Authoring):        53 tests ✓
Phase 361-365 (Content Model + Aspect):       6 tests ✓
Phase 364B (Lock Enhancement):               38 tests ✓
Phase 368A (Working Copy):                   54 tests ✓
Existing (Relations + ShareLink + Lock):     21 tests ✓
───────────────────────────────────────────────────────
Total:                                      254 tests, 0 failures
BUILD SUCCESS
```

## 5. Association Surface Entry Points

| Surface | Entry | Scope |
|---------|-------|-------|
| **FileList context menu** | Right-click → "Associations" | Per-document |
| **AssociationManager dialog** | 4-tab CRUD workbench | All association types |
