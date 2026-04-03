# Phase 369AD — Blog Backbone

> **Scope**: Site blog with draft/published workflow, activity integration, frontend page
> **Date**: 2026-04-03

---

## 1. What Was Built

### Backend

**BlogPost entity** — siteId, title, content, status (DRAFT/PUBLISHED), publishedDate, tags (JSONB).

**BlogService** — full lifecycle with author/admin permissions and activity integration:

| Method | Description |
|--------|-------------|
| `createPost(siteId, title, content, tags)` | Creates DRAFT; validates title |
| `publish(postId)` | DRAFT→PUBLISHED + sets publishedDate + posts `blog.published` activity |
| `unpublish(postId)` | PUBLISHED→DRAFT + clears publishedDate |
| `updatePost(postId, title, content, tags)` | Author/admin only; blank-title guard |
| `deletePost(postId)` | Author/admin only |
| `getPost(postId)` | Single post lookup |
| `listPosts(siteId, status?, pageable)` | All or filtered by status |
| `listDrafts(siteId, pageable)` | Drafts only |

**Activity integration** — `publish()` calls `activityEventListener.postSiteActivity("blog.published", ...)` which:
- Creates an Activity with siteId
- Routes to follower inboxes via NotificationInboxService
- Visible in site activity feed and Following feed

**BlogController** — 8 site-scoped endpoints:

| Method | Path | Description |
|--------|------|-------------|
| GET | `/sites/{siteId}/blog/posts` | List (optional status filter) |
| GET | `/sites/{siteId}/blog/posts/drafts` | Drafts only |
| POST | `/sites/{siteId}/blog/posts` | Create draft |
| GET | `/sites/{siteId}/blog/posts/{id}` | Get post |
| PUT | `/sites/{siteId}/blog/posts/{id}` | Update |
| POST | `/sites/{siteId}/blog/posts/{id}/publish` | Publish |
| POST | `/sites/{siteId}/blog/posts/{id}/unpublish` | Unpublish |
| DELETE | `/sites/{siteId}/blog/posts/{id}` | Delete |

**DB Migration 053** — `blog_posts` table with 3 indexes.

### Frontend

**blogService.ts** — 8 API methods matching all endpoints.

**BlogPage.tsx** — two-pane blog view:
- Left: post list with filter (All/Published/Drafts), status chip, author/date, tags
- Right: selected post content preview
- Author/admin: publish/unpublish/delete buttons (gated by `canModify`)
- New Post dialog: title + content + tags
- Pagination

**SitesPage.tsx** — "Open Blog" link in site detail panel.

**Routing** — `/sites/:siteId/blog` route in App.tsx.

### Notification Flow

```
BlogService.publish() → ActivityEventListener.postSiteActivity("blog.published")
  → ActivityService.postActivity() → NotificationInboxService.routeActivityToFollowers()
  → Followers of the site receive notification in inbox
```

## 2. Files Created

| File | Purpose |
|------|---------|
| `entity/BlogPost.java` | Blog post entity |
| `repository/BlogPostRepository.java` | Blog queries |
| `service/BlogService.java` | Blog lifecycle with activity integration |
| `controller/BlogController.java` | 8 REST endpoints |
| `db/changelog/changes/053-create-blog-posts-table.xml` | Migration |
| `services/blogService.ts` | Frontend API service |
| `pages/BlogPage.tsx` | Blog page |
| `test/service/BlogServiceTest.java` | 10 focused tests |

## 3. Files Modified

| File | Change |
|------|--------|
| `App.tsx` | +BlogPage import + `/sites/:siteId/blog` route |
| `pages/SitesPage.tsx` | +"Open Blog" card in site detail |
| `db/changelog/db.changelog-master.xml` | +053 |

## 4. NOT Modified

All preview/rendition/search/ops-governance files untouched.
