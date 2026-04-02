# Phase 368ZSA — Activity Feed Contract Completion

> **Scope**: Fix lint, wire siteId resolution, membership activity events, focused tests
> **Date**: 2026-04-01

---

## 1. What Was Fixed/Added

### Frontend Lint Fixes

- Removed unused `ActivityDto` import (was imported but only `ActivityPage` was used)
- Removed unused `activityIcon` function (was defined but never called)
- `eslint` now passes with 0 warnings on both `ActivityFeedPage.tsx` and `activityService.ts`

### Site ID Resolution

`ActivityEventListener.resolveSiteId(Node)` — resolves the siteId for any node by matching its path against site rootFolder paths:
- Iterates all active sites
- Checks if `nodePath.startsWith(rootFolderPath + "/")` or `equals(rootFolderPath)`
- Returns the first matching `siteId`, or `null` if none

Now all 8 event listeners pass the resolved `siteId` instead of `null`.

### Membership Activity Events

`SiteMembershipService` now posts activity entries for:
- `site.membership.requested` — when a user creates a membership request
- `site.membership.approved` — when admin approves
- `site.membership.rejected` — when admin rejects

These flow through `ActivityEventListener.postMembershipActivity()` and land in the activities table with `siteId` set.

### Focused Tests

`ActivityEventListenerTest.java` — 5 tests for site resolution:
- Node under site root → returns siteId
- Node equals site root → returns siteId
- Node not under any site → null
- Null node → null
- Site without root folder → null

## 2. Files Changed

### New Files

| File | Purpose |
|------|---------|
| `test/service/ActivityEventListenerTest.java` | 5 site resolution tests |

### Modified Files

| File | Change |
|------|--------|
| `service/ActivityEventListener.java` | +SiteRepository injection; +`resolveSiteId()`; all listeners pass resolved siteId; +`postMembershipActivity()` |
| `service/SiteMembershipService.java` | +ActivityEventListener injection; posts activity on createRequest, approve, reject |
| `pages/ActivityFeedPage.tsx` | Removed unused ActivityDto import + activityIcon function |
| `test/.../SiteMembershipServiceTest.java` | +activityEventListener mock |
| `test/.../SiteMemberRosterTest.java` | +activityEventListener mock |

## 3. NOT Modified

All preview/rendition/search/ops-governance files untouched.
