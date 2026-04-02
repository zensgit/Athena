# Phase 368ZR — Site Members Roster And Detail Surface — Verification

> **Date**: 2026-04-01

---

## 1. Verification Matrix

| # | Claim | Status |
|---|-------|--------|
| 1 | SiteMember entity with site FK, username, role, joinedAt | PASS |
| 2 | SiteMemberRole enum: MANAGER, COLLABORATOR, CONTRIBUTOR, CONSUMER | PASS |
| 3 | Unique constraint (site_id, username) | PASS |
| 4 | getMembers returns members sorted by role then username | PASS |
| 5 | addMember creates with specified role | PASS |
| 6 | addMember rejects duplicate | PASS |
| 7 | addMember non-admin rejected | PASS |
| 8 | updateMemberRole changes role | PASS |
| 9 | updateMemberRole throws when not found | PASS |
| 10 | removeMember delegates to repo | PASS |
| 11 | getUserSites returns user's memberships | PASS |
| 12 | GET /sites/{id}/members endpoint | PASS |
| 13 | POST /sites/{id}/members endpoint (admin) | PASS |
| 14 | PUT /sites/{id}/members/{username} endpoint (admin) | PASS |
| 15 | DELETE /sites/{id}/members/{username} endpoint (admin) | PASS |
| 16 | Frontend siteService has getMembers/addMember/updateMemberRole/removeMember | PASS |
| 17 | SitesPage shows member roster on site click | PASS |
| 18 | SitesPage shows site-specific requests on click | PASS |
| 19 | SitesPage admin add-member form | PASS |
| 20 | SitesPage admin remove-member button | PASS |
| 21 | SitesPage "Open Workspace" button | PASS |
| 22 | Migration 047 creates site_members table | PASS |

## 2. Hot-File Constraint

Zero preview/rendition/search/ops-governance files modified.

## 3. Test Inventory

### SiteMemberRosterTest.java — 8 tests

```
GetMembers (1):
  ✓ returns all members for a site

AddMember (3):
  ✓ adds member with specified role
  ✓ rejects duplicate member
  ✓ non-admin cannot add members

UpdateRole (2):
  ✓ updates member role
  ✓ throws when member not found

RemoveMember (1):
  ✓ removes member by username

UserSites (1):
  ✓ returns sites a user belongs to
```

## 4. Full Regression

```
Phase 368ZR (Site Members Roster):            8 tests ✓
Phase 368ZQ (Site Membership Backend):        8 tests ✓
Phase 368ZP (Sites Membership Surface):       6 tests ✓
Phase 368ZM (Batch Item Helper):              4 tests ✓
Phase 368ZE (User Preferences):              20 tests ✓
Phase 368ZC-ZD (Rating / Likes):             15 tests ✓
Phase 368Y (Discovery API):                   6 tests ✓
Phase 368X (Association Operator Surface):     7 tests ✓
Phase 368W-V (Shared Links):                 24 tests ✓
Phase 368T (Shared Links Enhancement):         9 tests ✓
Phase 368R (Node Associations):               10 tests ✓
Phase 368Q (Type Enforcement):                14 tests ✓
Phase 368O (Request Contract):                11 tests ✓
Phase 368M (Aspect Property Enforcement):     13 tests ✓
Phase 368K (Content Model Authoring):         53 tests ✓
Phase 361-365 (Content Model + Aspect):        6 tests ✓
Phase 364B (Lock Enhancement):                38 tests ✓
Phase 368A (Working Copy):                    54 tests ✓
Existing tests:                               25 tests ✓
────────────────────────────────────────────────────────
Total:                                       331 tests, 0 failures
BUILD SUCCESS
```

## 5. Site Feature Completeness (after 368ZR)

| Capability | Backend | Frontend | Tests |
|------------|:-------:|:--------:|:-----:|
| Site CRUD | ✅ | ✅ | ✅ |
| **Member roster (list/add/update/remove)** | ✅ | ✅ | ✅ |
| **Member role management** | ✅ | ✅ | ✅ |
| Membership requests (create/approve/reject/withdraw) | ✅ | ✅ | ✅ |
| Site-centric request drill-down | ✅ | ✅ | — |
| Root folder workspace link | ✅ | ✅ | — |
| Sidebar navigation | — | ✅ | — |
| Route `/sites` | — | ✅ | — |
| DB: sites table | ✅ | — | — |
| **DB: site_members table** | ✅ | — | — |

**Ready for Phase 368ZS: Activity Feed Backbone.**
