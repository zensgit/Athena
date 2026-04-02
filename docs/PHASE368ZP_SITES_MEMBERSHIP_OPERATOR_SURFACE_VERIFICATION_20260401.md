# Phase 368ZP — Sites Membership And Operator Surface — Verification

> **Date**: 2026-04-01

---

## 1. Verification Matrix

| # | Claim | Status |
|---|-------|--------|
| 1 | `siteService.ts` has listSites/getSite/createSite/updateSite/deleteSite | PASS |
| 2 | `SitesPage.tsx` renders site registry table | PASS |
| 3 | Site table shows siteId, title, visibility, status, root folder | PASS |
| 4 | Visibility chip: Public (green), Moderated (warning), Private (red) | PASS |
| 5 | Toggle active-only / show-archived | PASS |
| 6 | Admin "New Site" button → create dialog | PASS |
| 7 | Create dialog: siteId, title, description, visibility | PASS |
| 8 | Admin archive button per row | PASS |
| 9 | Root folder chip → opens `/browse/{id}` | PASS |
| 10 | "My Membership Requests" panel shows user's requests | PASS |
| 11 | "Request Membership" button → request dialog | PASS |
| 12 | Request dialog: site dropdown, role, message | PASS |
| 13 | Admin Approve/Reject buttons on pending requests | PASS |
| 14 | Withdraw button on user's pending requests | PASS |
| 15 | `/sites` route in App.tsx | PASS |
| 16 | "Sites" menu item in sidebar | PASS |
| 17 | GET /sites returns list with visibility/status | PASS |
| 18 | GET /sites passes includeArchived param | PASS |
| 19 | GET /sites/{siteId} returns single site | PASS |
| 20 | POST /sites creates and returns 201 | PASS |
| 21 | PUT /sites/{siteId} updates fields | PASS |
| 22 | DELETE /sites/{siteId} archives and returns 204 | PASS |

## 2. Hot-File Constraint

Zero preview/rendition/search/ops-governance files modified.

## 3. Test Inventory

### SiteMembershipContractTest.java — 6 tests

```
ListSites (2):
  ✓ returns site list with visibility and status
  ✓ passes includeArchived parameter

GetSite (1):
  ✓ returns site by siteId

CreateSite (1):
  ✓ creates site and returns 201

UpdateSite (1):
  ✓ updates site fields

DeleteSite (1):
  ✓ archives site and returns 204
```

### Existing site tests (still passing)

```
SiteServiceTest:    4 tests ✓
SiteControllerTest: 4 tests ✓
```

## 4. Full Regression

```
Phase 368ZP (Sites Membership):               6 tests ✓
Phase 368ZM (Batch Item Helper):              4 tests ✓
Phase 368ZE (User Preferences):              20 tests ✓
Phase 368ZC-ZD (Rating / Likes):             15 tests ✓
Phase 368Y (Discovery API):                   6 tests ✓
Phase 368X (Association Operator Surface):     7 tests ✓
Phase 368W (Cross-Surface Entry):              4 tests ✓
Phase 368V (Admin Governance Surface):        10 tests ✓
Phase 368U (Operator Surface Convergence):     4 tests ✓
Phase 368T (Shared Links Enhancement):         9 tests ✓
Phase 368R (Node Associations):               10 tests ✓
Phase 368Q (Type Enforcement):                14 tests ✓
Phase 368O (Request Contract):                11 tests ✓
Phase 368M (Aspect Property Enforcement):     13 tests ✓
Phase 368K (Content Model Authoring):         53 tests ✓
Phase 361-365 (Content Model + Aspect):        6 tests ✓
Phase 364B (Lock Enhancement):                38 tests ✓
Phase 368A (Working Copy):                    54 tests ✓
Existing tests (Sites + Relations + Lock):    31 tests ✓
────────────────────────────────────────────────────────
Total:                                       315 tests, 0 failures
BUILD SUCCESS
```

## 5. Sites Feature Coverage

| Capability | Backend | Frontend | Tests |
|------------|:-------:|:--------:|:-----:|
| Site CRUD (create/read/update/archive) | ✅ | ✅ SitesPage | ✅ |
| Site visibility (Public/Moderated/Private) | ✅ | ✅ chip | ✅ |
| Site status (Active/Archived) | ✅ | ✅ toggle | ✅ |
| Root folder association | ✅ | ✅ browse link | — |
| Membership request (create/update/withdraw) | ✅ | ✅ dialog | — |
| Membership moderation (approve/reject) | ✅ | ✅ buttons (admin) | — |
| Sidebar navigation | — | ✅ menu item | — |
| Route `/sites` | — | ✅ App.tsx | — |
