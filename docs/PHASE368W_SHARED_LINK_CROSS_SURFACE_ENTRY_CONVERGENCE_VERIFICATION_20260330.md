# Phase 368W — Shared Link Cross-Surface Entry Convergence — Verification

> **Date**: 2026-03-30

---

## 1. Verification Matrix

| # | Claim | Status |
|---|-------|--------|
| 1 | AdminDashboard open-node route fixed to `/browse/:nodeId` | PASS |
| 2 | DocumentPreview has "Share" in MoreVert action menu | PASS |
| 3 | DocumentPreview Share only shown for documents + canWrite | PASS |
| 4 | DocumentPreview Share dispatches setSelectedNodeId + setShareLinkManagerOpen | PASS |
| 5 | SearchResults has "Share" button per document row | PASS |
| 6 | SearchResults Share only shown for documents + canWrite | PASS |
| 7 | SearchResults Share dispatches to Redux uiSlice | PASS |
| 8 | ShareLinkResponse includes nodeId for admin navigation | PASS |
| 9 | ShareLinkResponse includes nodeName for display | PASS |
| 10 | Reactivate response includes nodeId for refresh | PASS |
| 11 | Access log includes clientIp + userAgent for audit | PASS |
| 12 | Admin all returns passwordProtected + hasIpRestrictions flags | PASS |

## 2. Hot-File Constraint

Zero backend preview/rendition/search/ops-governance files modified. The frontend `SearchResults.tsx` is a **page component**, not a search service.

## 3. Test Inventory

### ShareLinkCrossSurfaceTest.java — 4 tests

```
  ✓ ShareLinkResponse includes nodeId and nodeName for admin navigation
  ✓ Reactivate returns nodeId so admin panel can refresh correctly
  ✓ Access log includes clientIp for security audit drill-down
  ✓ Admin all returns passwordProtected and hasIpRestrictions flags
```

## 4. Full Regression

```
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
Total:                                      247 tests, 0 failures
BUILD SUCCESS
```

## 5. Share Link Entry Point Matrix (after 368W)

| Surface | Entry Point | Trigger |
|---------|-------------|---------|
| **FileList** (context menu) | Right-click → "Share" | canWrite + DOCUMENT |
| **DocumentPreview** (action menu) | MoreVert → "Share" | canWrite + DOCUMENT |
| **SearchResults** (row actions) | "Share" button | canWrite + DOCUMENT |
| **AdminDashboard** (governance tab) | System-wide table | ROLE_ADMIN |

All 4 surfaces now converge on the same `ShareLinkManager` dialog via Redux dispatch (`setSelectedNodeId` + `setShareLinkManagerOpen`).
