# Phase 368ZS — Activity Feed Backbone — Verification

> **Date**: 2026-04-01

---

## 1. Verification Matrix

| # | Claim | Status |
|---|-------|--------|
| 1 | Activity entity with all fields | PASS |
| 2 | 5 indexes on activities table | PASS |
| 3 | postActivity creates with all fields | PASS |
| 4 | postActivity handles null summary | PASS |
| 5 | getUserFeed delegates to repo | PASS |
| 6 | getSiteFeed delegates to repo | PASS |
| 7 | getGlobalFeed delegates to repo | PASS |
| 8 | getNodeFeed delegates to repo | PASS |
| 9 | GET /activities returns paginated global feed | PASS |
| 10 | GET /activities/users/{id} returns user feed | PASS |
| 11 | GET /activities/sites/{id} returns site feed | PASS |
| 12 | GET /activities/nodes/{id} returns node feed | PASS |
| 13 | ActivityEventListener listens to 8 event types | PASS (compile) |
| 14 | @Async annotation on all listeners | PASS |
| 15 | Scheduled cleanup at 3 AM daily (90 days) | PASS (code review) |
| 16 | Frontend activityService has 4 methods | PASS |
| 17 | ActivityFeedPage with scope selector + pagination | PASS |
| 18 | /activities route in App.tsx | PASS |
| 19 | Sidebar menu item | PASS |
| 20 | Migration 048 registered | PASS |

## 2. Hot-File Constraint

Zero preview/rendition/search/ops-governance files modified.

## 3. Test Inventory

### ActivityServiceTest.java — 6 tests

```
PostActivity (2):
  ✓ creates activity with all fields
  ✓ handles null summary gracefully

FeedQueries (4):
  ✓ getUserFeed delegates to repository
  ✓ getSiteFeed delegates to repository
  ✓ getGlobalFeed delegates to repository
  ✓ getNodeFeed delegates to repository
```

### ActivityControllerTest.java — 4 tests

```
  ✓ GET /activities returns global feed with activity fields
  ✓ GET /activities/users/{userId} returns user feed
  ✓ GET /activities/sites/{siteId} returns site feed
  ✓ GET /activities/nodes/{nodeId} returns node feed
```

## 4. Full Regression

```
Phase 368ZS (Activity Feed):                 10 tests ✓
Phase 368ZR (Site Members):                   8 tests ✓
Phase 368ZQ (Site Membership Backend):        8 tests ✓
Phase 368ZP (Sites Surface):                  6 tests ✓
Phase 368ZM (Batch Item Helper):              4 tests ✓
Phase 368ZE (User Preferences):              20 tests ✓
Phase 368ZC-ZD (Rating / Likes):             15 tests ✓
Phase 368Y (Discovery API):                   6 tests ✓
Phase 368X (Association Surface):             7 tests ✓
Phase 368W-V (Shared Links):                 24 tests ✓
Phase 368T (Shared Links Enhancement):         9 tests ✓
Phase 368R (Node Associations):              10 tests ✓
Phase 368Q (Type Enforcement):               14 tests ✓
Phase 368O (Request Contract):               11 tests ✓
Phase 368M (Aspect Enforcement):             13 tests ✓
Phase 368K (Content Model):                  53 tests ✓
Phase 361-365 (Content Model + Aspect):       6 tests ✓
Phase 364B (Lock Enhancement):               38 tests ✓
Phase 368A (Working Copy):                   54 tests ✓
Existing tests:                              25 tests ✓
────────────────────────────────────────────────────────
Total:                                      341 tests, 0 failures
BUILD SUCCESS
```

## 5. Alfresco Activity Feed Parity

| Alfresco Capability | Athena |
|--------------------|:------:|
| Post activity | ✅ `postActivity()` |
| User activity feed | ✅ `GET /activities/users/{id}` |
| Site activity feed | ✅ `GET /activities/sites/{id}` |
| Global feed | ✅ `GET /activities` |
| Node-scoped feed | ✅ `GET /activities/nodes/{id}` |
| Activity types (node CRUD, version, comment) | ✅ 8 event types |
| Automatic from domain events | ✅ `@EventListener` |
| Async posting | ✅ `@Async` |
| Scheduled cleanup | ✅ Daily 3AM, 90-day retention |
| Frontend feed page | ✅ Scope selector + pagination |
| Summary (JSONB) | ✅ Arbitrary metadata |
