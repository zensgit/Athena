# Phase 368ZP — Sites Membership And Operator Surface

> **Scope**: Frontend site service, SitesPage, /sites route, membership request UI, focused tests
> **Date**: 2026-04-01

---

## 1. Problem Statement

Backend had full Site entity/service/controller + membership request endpoints in
PeopleController, but the frontend had:
- No `siteService.ts` for site registry CRUD
- No `SitesPage.tsx` for managing sites
- No `/sites` route in App.tsx
- No sidebar menu entry for Sites
- Membership request UI only lived deep in PeopleDirectoryPage

## 2. What Was Built

### Frontend Service: `siteService.ts`

| Method | HTTP | Endpoint |
|--------|------|----------|
| `listSites(includeArchived?)` | GET | `/sites` |
| `getSite(siteId)` | GET | `/sites/{siteId}` |
| `createSite(request)` | POST | `/sites` |
| `updateSite(siteId, request)` | PUT | `/sites/{siteId}` |
| `deleteSite(siteId)` | DELETE | `/sites/{siteId}` |

TypeScript types: `SiteDto`, `SiteVisibility`, `SiteStatus`, `CreateSiteRequest`, `UpdateSiteRequest`

### Frontend Page: `SitesPage.tsx`

Two-column layout:

| Left (8 cols) — Site Registry | Right (4 cols) — My Requests |
|-------------------------------|------------------------------|
| Table: Site ID, Title, Visibility, Status, Root Folder, Actions | Card list of pending membership requests |
| Toggle: Active only / Show archived | Per-request: Approve/Reject (admin), Withdraw |
| Admin: "New Site" button → create dialog | |
| Admin: Archive button per row | |
| Root folder → "Browse" chip → `/browse/{id}` | |

**Dialogs:**
- Create Site: siteId, title, description, visibility (Public/Moderated/Private)
- Request Membership: site dropdown, role (Consumer/Contributor/Collaborator/Manager), message

### Routing + Navigation

- `/sites` route added to `App.tsx` (PrivateRoute, any authenticated user)
- "Sites" menu item added to MainLayout sidebar (after People Directory)

### Backend Tests: `SiteMembershipContractTest.java`

6 MockMvc tests verifying the SiteController REST contract.

## 3. Files Created

| File | Purpose |
|------|---------|
| `services/siteService.ts` | Frontend API service for site registry |
| `pages/SitesPage.tsx` | Site management + membership request UI |
| `test/controller/SiteMembershipContractTest.java` | 6 endpoint contract tests |

## 4. Files Modified

| File | Change |
|------|--------|
| `App.tsx` | +SitesPage import, +`/sites` route |
| `components/layout/MainLayout.tsx` | +"Sites" sidebar menu item |

## 5. NOT Modified

All preview/rendition/search/ops-governance files untouched. Backend Site entity/service/controller unchanged.
