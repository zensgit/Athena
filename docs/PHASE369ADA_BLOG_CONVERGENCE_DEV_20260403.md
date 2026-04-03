# Phase 369ADA — Blog Convergence

> **Scope**: Complete activity coverage, drafts sorting fix, edit operator surface, focused tests
> **Date**: 2026-04-03

---

## 1. What Was Fixed/Added

### Backend — Activity coverage

| Event | Activity Type | Before | After |
|-------|-------------|:------:|:-----:|
| Create post | `blog.created` | Missing | ✅ |
| Publish post | `blog.published` | ✅ | ✅ |
| Unpublish post | `blog.unpublished` | Missing | ✅ |
| Update post | `blog.updated` | Missing | ✅ |

All 4 activity types route through `ActivityEventListener.postSiteActivity()` → `NotificationInboxService` → follower inboxes.

### Backend — Drafts sort fix

`listDrafts` was using `OrderByPublishedDateDesc` — but drafts have null publishedDate. Fixed to `OrderByCreatedDateDesc`. Added `findBySiteIdAndStatusOrderByCreatedDateDesc` to `BlogPostRepository`.

### Frontend — Edit dialog

`BlogPage.tsx` now has a complete edit operator surface:
- Edit button (pencil icon) in post card actions (author/admin only)
- Edit dialog: pre-populated title + content + tags
- `handleEdit()` calls `blogService.updatePost()`
- Refresh after save

### Tests — +2 new

- `updatePostsActivity` — verifies `blog.updated` activity posted
- `unpublishPostsActivity` — verifies `blog.unpublished` activity posted
- `createsDraft` — now also verifies `blog.created` activity posted

## 2. Files Modified

| File | Change |
|------|--------|
| `service/BlogService.java` | +blog.created/updated/unpublished activities; listDrafts sort fix |
| `repository/BlogPostRepository.java` | +`findBySiteIdAndStatusOrderByCreatedDateDesc` |
| `test/service/BlogServiceTest.java` | +2 activity tests, createsDraft updated with activity verify |
| `pages/BlogPage.tsx` | +Edit dialog + openEdit/handleEdit + Edit icon button |

## 3. NOT Modified

All preview/rendition/search/ops-governance files untouched.
