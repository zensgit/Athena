# Phase 368ZQ — Site Membership Backend Convergence — Verification

> **Date**: 2026-04-01

---

## 1. Verification Matrix

| # | Claim | Status |
|---|-------|--------|
| 1 | SiteMembershipService extracted as @Service | PASS |
| 2 | createRequest creates PENDING entry in preferences | PASS |
| 3 | createRequest rejects duplicate for same site | PASS |
| 4 | approve sets APPROVED + decision metadata | PASS |
| 5 | reject sets REJECTED | PASS |
| 6 | non-admin cannot moderate | PASS |
| 7 | withdraw removes request from preferences | PASS |
| 8 | withdraw throws when request not found | PASS |
| 9 | getRequestsForSite aggregates across users | PASS |
| 10 | SiteController has GET /sites/{siteId}/membership-requests | PASS |
| 11 | SiteController has POST /sites/{siteId}/membership-requests | PASS |
| 12 | SiteController has POST .../approve | PASS |
| 13 | SiteController has POST .../reject | PASS |
| 14 | SiteController has DELETE .../membership-requests | PASS |
| 15 | Frontend siteService has 5 membership methods | PASS |
| 16 | SitesPage consumes /sites/... not /people/... | PASS |
| 17 | Admin sees username on requests from other users | PASS |
| 18 | PeopleController endpoints still exist (backward compat) | PASS |

## 2. Hot-File Constraint

Zero preview/rendition/search/ops-governance files modified.

## 3. Test Inventory

### SiteMembershipServiceTest.java — 8 tests

```
CreateRequest (2):
  ✓ creates pending request in user preferences
  ✓ rejects duplicate request for same site

Moderation (3):
  ✓ approve sets status to APPROVED with decision metadata
  ✓ reject sets status to REJECTED
  ✓ non-admin cannot moderate

Withdraw (2):
  ✓ removes request from preferences
  ✓ throws when request not found

QueryBySite (1):
  ✓ returns requests matching siteId across users
```

## 4. Full Regression

```
Phase 368ZQ (Site Membership Backend):        8 tests ✓
Phase 368ZP (Sites Membership Surface):       6 tests ✓
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
Existing tests:                               31 tests ✓
────────────────────────────────────────────────────────
Total:                                       323 tests, 0 failures
BUILD SUCCESS
```
