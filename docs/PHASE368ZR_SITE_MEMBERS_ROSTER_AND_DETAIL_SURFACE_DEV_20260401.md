# Phase 368ZR — Site Members Roster And Detail Surface

> **Scope**: SiteMember entity, member CRUD endpoints, SitesPage member roster + request drill-down
> **Date**: 2026-04-01

---

## 1. Problem Statement

Phase 368ZQ delivered site-centric membership *request* endpoints but there was no
actual member roster — no `SiteMember` entity, no way to list/add/remove/update
members, and the frontend had no detail panel for a selected site.

## 2. What Was Built

### SiteMember Entity (new)

```java
@Entity @Table(name = "site_members")
SiteMember {
    UUID id;
    Site site;              // FK → sites
    String username;        // member username
    SiteMemberRole role;    // MANAGER | COLLABORATOR | CONTRIBUTOR | CONSUMER
    LocalDateTime joinedAt; // auto-set on creation
}
```

Unique constraint: `(site_id, username)` — one membership per user per site.

### DB Migration 047

Creates `site_members` table with FK to sites, unique constraint, and indexes on site_id + username.

### Service Methods (5 new on SiteMembershipService)

| Method | Description |
|--------|-------------|
| `getMembers(siteId)` | List all members sorted by role then username |
| `addMember(siteId, username, role)` | Add member (admin only, validates user exists, rejects duplicates) |
| `updateMemberRole(siteId, username, role)` | Update member role (admin only) |
| `removeMember(siteId, username)` | Remove member (admin only) |
| `getUserSites(username)` | List sites a user belongs to |

### Controller Endpoints (4 new)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/sites/{siteId}/members` | List members with roles |
| POST | `/sites/{siteId}/members` | Add member (admin) |
| PUT | `/sites/{siteId}/members/{username}` | Update role (admin) |
| DELETE | `/sites/{siteId}/members/{username}` | Remove member (admin) |

### Frontend

**siteService.ts** — 4 new member methods + `SiteMemberDto` type.

**SitesPage.tsx** — enhanced with click-to-select site detail panel:
- Left: **Members roster** table (username, role chip, joined date, admin remove)
- Right: **Site requests** card list for the selected site (approve/reject drill-down)
- Admin: **Add member** form (username + role dropdown)
- **Open Workspace** button → `/browse/{rootFolderId}` in new tab
- Site table rows are now clickable (highlight selected)

## 3. Files Created

| File | Purpose |
|------|---------|
| `entity/SiteMember.java` | Member entity + SiteMemberRole enum |
| `repository/SiteMemberRepository.java` | Member queries |
| `db/changelog/changes/047-create-site-members-table.xml` | Migration |
| `test/service/SiteMemberRosterTest.java` | 8 focused tests |

## 4. Files Modified

| File | Change |
|------|--------|
| `service/SiteMembershipService.java` | +SiteRepository/SiteMemberRepository deps, +5 member methods, +3 DTOs |
| `controller/SiteController.java` | +SiteMemberRole import, +SiteMemberDto import, +4 member endpoints |
| `services/siteService.ts` | +4 member methods, +SiteMemberDto type |
| `pages/SitesPage.tsx` | +site detail panel (members + requests), clickable rows, add member form |
| `db/changelog/db.changelog-master.xml` | +047 |
| `test/.../SiteMembershipServiceTest.java` | Constructor fix for new deps |

## 5. NOT Modified

All preview/rendition/search/ops-governance files untouched.
