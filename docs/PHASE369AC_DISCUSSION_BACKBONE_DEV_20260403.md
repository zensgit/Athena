# Phase 369AC — Discussion Backbone

> **Scope**: Site discussion forum — topics + threaded replies
> **Date**: 2026-04-03

---

## 1. What Was Built

### Backend

**Entities:**
- `DiscussionTopic` — siteId, title, content, status (OPEN/CLOSED/PINNED), tags (JSONB)
- `DiscussionReply` — topicId FK, parentReplyId (threading), content

**DiscussionService** — topic CRUD (create/list/get/update/delete) + reply CRUD (create/list/update/delete):
- Title required, blank rejected
- Replies blocked on CLOSED topics
- Reply edit/delete restricted to author or admin

**DiscussionController** — site-scoped endpoints:

| Method | Path | Description |
|--------|------|-------------|
| GET | `/sites/{siteId}/discussions` | List topics (paginated, optional status filter) |
| POST | `/sites/{siteId}/discussions` | Create topic |
| GET | `/sites/{siteId}/discussions/{topicId}` | Get topic |
| PUT | `/sites/{siteId}/discussions/{topicId}` | Update topic |
| DELETE | `/sites/{siteId}/discussions/{topicId}` | Delete topic + cascade replies |
| GET | `/sites/{siteId}/discussions/{topicId}/replies` | List replies |
| POST | `/sites/{siteId}/discussions/{topicId}/replies` | Create reply (optional parentReplyId) |
| PUT | `/sites/{siteId}/discussions/{topicId}/replies/{replyId}` | Edit reply |
| DELETE | `/sites/{siteId}/discussions/{topicId}/replies/{replyId}` | Delete reply |

**DB Migration 052** — `discussion_topics` + `discussion_replies` tables with indexes.

### Frontend

**discussionService.ts** — 9 methods covering all topic + reply CRUD.

**DiscussionPage.tsx** — full forum view:
- Two-pane: topic list (left) + reply thread (right)
- Topic cards: title, status icon (pinned/closed), author, date, reply count, tags, delete
- Reply thread: chronological, nested indent for threaded replies, inline reply box
- New Topic dialog: title + content
- Closed topics show "closed for replies" message

**SitesPage.tsx** — "Discussions" card in site detail with "Open Forum" link.

**Routing** — `/sites/:siteId/discussions` route in App.tsx.

## 2. Files Created

| File | Purpose |
|------|---------|
| `entity/DiscussionTopic.java` | Topic entity |
| `entity/DiscussionReply.java` | Reply entity (threaded) |
| `repository/DiscussionTopicRepository.java` | Topic queries |
| `repository/DiscussionReplyRepository.java` | Reply queries |
| `service/DiscussionService.java` | Topic + reply business logic |
| `controller/DiscussionController.java` | 9 REST endpoints + DTOs |
| `db/changelog/changes/052-create-discussion-tables.xml` | Migration |
| `services/discussionService.ts` | Frontend API service |
| `pages/DiscussionPage.tsx` | Forum page |
| `test/service/DiscussionServiceTest.java` | 9 focused tests |

## 3. Files Modified

| File | Change |
|------|--------|
| `App.tsx` | +DiscussionPage import + `/sites/:siteId/discussions` route |
| `pages/SitesPage.tsx` | +Discussions card in site detail panel |
| `db/changelog/db.changelog-master.xml` | +052 |

## 4. NOT Modified

All preview/rendition/search/ops-governance files untouched.
