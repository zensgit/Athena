# Phase 369ADA — Blog Convergence — Verification

> **Date**: 2026-04-03

---

## 1. Verification Matrix

| # | Claim | Status |
|---|-------|--------|
| 1 | createPost posts blog.created activity | PASS |
| 2 | publish posts blog.published activity (unchanged) | PASS |
| 3 | unpublish posts blog.unpublished activity | PASS |
| 4 | updatePost posts blog.updated activity | PASS |
| 5 | listDrafts sorts by createdDate (not publishedDate) | PASS |
| 6 | BlogPage has Edit button (pencil icon) | PASS |
| 7 | Edit button gated by canModify (author/admin) | PASS |
| 8 | Edit dialog pre-populates title/content/tags | PASS |
| 9 | Edit dialog calls updatePost and refreshes | PASS |
| 10 | eslint 0 warnings on BlogPage + blogService | PASS |

## 2. Test Inventory — 12 tests (was 10)

```
Create (2):
  ✓ creates draft post and posts blog.created activity  [UPDATED]
  ✓ rejects blank title

Lifecycle (4):
  ✓ publish sets PUBLISHED and posts blog.published
  ✓ publish rejects already published
  ✓ unpublish reverts to draft
  ✓ non-author cannot publish

Permissions (6, was 4):
  ✓ author can update
  ✓ non-author cannot update
  ✓ author can delete
  ✓ update rejects blank title
  ✓ update posts blog.updated activity              [NEW]
  ✓ unpublish posts blog.unpublished activity        [NEW]
```

## 3. Blog Activity Types (complete)

| Action | Activity Type | Routed to |
|--------|-------------|-----------|
| Create | `blog.created` | Site followers |
| Update | `blog.updated` | Site followers |
| Publish | `blog.published` | Site followers |
| Unpublish | `blog.unpublished` | Site followers |
