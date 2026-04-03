# Phase 369ACA — Discussion Hardening — Verification

> **Date**: 2026-04-03

---

## 1. Verification Matrix

| # | Claim | Status |
|---|-------|--------|
| 1 | updateTopic checks author/admin | PASS |
| 2 | non-author non-admin cannot updateTopic | PASS |
| 3 | admin can update any topic | PASS |
| 4 | updateTopic rejects blank title after trim | PASS |
| 5 | deleteTopic checks author/admin | PASS |
| 6 | non-author non-admin cannot deleteTopic | PASS |
| 7 | Frontend delete button gated by author/admin | PASS |
| 8 | Frontend eslint 0 warnings on DiscussionPage | PASS |
| 9 | Reply author check unchanged (already correct) | PASS |

## 2. Test Inventory

### DiscussionServiceTest.java — 13 tests (was 9)

```
Topics (8, was 4):
  ✓ creates topic with title and siteId
  ✓ rejects blank title on create
  ✓ author can update topic fields          [NEW]
  ✓ non-author non-admin cannot update      [NEW]
  ✓ admin can update any topic              [NEW]
  ✓ update rejects blank title after trim   [NEW]
  ✓ author can delete topic                 [REPLACED]
  ✓ non-author non-admin cannot delete      [NEW]

Replies (5, unchanged):
  ✓ creates reply on open topic
  ✓ rejects reply on closed topic
  ✓ rejects blank reply content
  ✓ author can edit own reply
  ✓ non-author non-admin cannot edit reply
```

## 3. Permission Matrix (after hardening)

| Operation | Author | Admin | Other |
|-----------|:------:|:-----:|:-----:|
| Create topic | Any | Any | Any |
| Update topic | ✅ | ✅ | ❌ |
| Delete topic | ✅ | ✅ | ❌ |
| Create reply (open) | Any | Any | Any |
| Edit reply | ✅ | ✅ | ❌ |
| Delete reply | ✅ | ✅ | ❌ |
| Reply to closed topic | ❌ | ❌ | ❌ |
