# Phase 369AC — Discussion Backbone — Verification

> **Date**: 2026-04-03

---

## 1. Verification Matrix

| # | Claim | Status |
|---|-------|--------|
| 1 | DiscussionTopic entity with siteId, title, content, status, tags | PASS |
| 2 | DiscussionReply entity with topicId FK, parentReplyId, content | PASS |
| 3 | createTopic with title + siteId | PASS |
| 4 | createTopic rejects blank title | PASS |
| 5 | updateTopic changes title/content/status | PASS |
| 6 | deleteTopic cascades replies | PASS |
| 7 | createReply on open topic | PASS |
| 8 | createReply rejected on CLOSED topic | PASS |
| 9 | createReply rejects blank content | PASS |
| 10 | updateReply by author succeeds | PASS |
| 11 | updateReply by non-author non-admin rejected | PASS |
| 12 | 9 REST endpoints on /sites/{siteId}/discussions | PASS |
| 13 | Frontend discussionService has 9 methods | PASS |
| 14 | DiscussionPage two-pane forum view | PASS |
| 15 | Topic status icons (pinned/closed) | PASS |
| 16 | Threaded reply indent | PASS |
| 17 | SitesPage "Open Forum" link | PASS |
| 18 | /sites/:siteId/discussions route | PASS |
| 19 | Migration 052 creates 2 tables | PASS |

## 2. Hot-File Constraint

Zero preview/rendition/search/ops-governance files modified.

## 3. Test Inventory

### DiscussionServiceTest.java — 9 tests

```
Topics (4):
  ✓ creates topic with title and siteId
  ✓ rejects blank title
  ✓ updates topic fields
  ✓ deletes topic

Replies (5):
  ✓ creates reply on open topic
  ✓ rejects reply on closed topic
  ✓ rejects blank reply content
  ✓ author can edit own reply
  ✓ non-author non-admin cannot edit reply
```
