# Phase 369AD — Blog Backbone — Verification

> **Date**: 2026-04-03

---

## 1. Verification Matrix

| # | Claim | Status |
|---|-------|--------|
| 1 | BlogPost entity with DRAFT/PUBLISHED status | PASS |
| 2 | createPost creates DRAFT with title validation | PASS |
| 3 | createPost rejects blank title | PASS |
| 4 | publish transitions DRAFT→PUBLISHED + sets publishedDate | PASS |
| 5 | publish rejects already-published post | PASS |
| 6 | publish posts blog.published activity | PASS |
| 7 | unpublish reverts to DRAFT + clears publishedDate | PASS |
| 8 | non-author non-admin cannot publish | PASS |
| 9 | author can update post | PASS |
| 10 | non-author non-admin cannot update | PASS |
| 11 | author can delete post | PASS |
| 12 | update rejects blank title | PASS |
| 13 | Frontend blogService has 8 methods | PASS |
| 14 | BlogPage filter All/Published/Drafts | PASS |
| 15 | BlogPage publish/unpublish/delete gated by canModify | PASS |
| 16 | SitesPage "Open Blog" link | PASS |
| 17 | /sites/:siteId/blog route | PASS |
| 18 | Migration 053 creates blog_posts table | PASS |
| 19 | eslint 0 warnings on BlogPage + blogService | PASS |

## 2. Hot-File Constraint

Zero preview/rendition/search/ops-governance files modified.

## 3. Test Inventory

### BlogServiceTest.java — 10 tests

```
Create (2):
  ✓ creates draft post with title and siteId
  ✓ rejects blank title

Lifecycle (4):
  ✓ publish sets PUBLISHED status and publishedDate
  ✓ publish rejects already published
  ✓ unpublish reverts to draft
  ✓ non-author non-admin cannot publish

Permissions (4):
  ✓ author can update
  ✓ non-author non-admin cannot update
  ✓ author can delete
  ✓ update rejects blank title
```

## 4. Activity / Notification Integration

| Event | Activity Type | Routed to |
|-------|-------------|-----------|
| Blog published | `blog.published` | Followers of site |

Via the existing pipeline: `ActivityEventListener.postSiteActivity()` → `ActivityService.postActivity()` → `NotificationInboxService.routeActivityToFollowers()`.
