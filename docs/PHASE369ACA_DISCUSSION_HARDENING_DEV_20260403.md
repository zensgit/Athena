# Phase 369ACA — Discussion Hardening

> **Scope**: Author/admin permission checks on topic update/delete, blank-title guard, frontend action visibility, lint, focused tests
> **Date**: 2026-04-03

---

## 1. Problem Statement

Phase 369AC delivered the Discussion backbone but:

| Gap | Risk |
|-----|------|
| `updateTopic` had no author/admin check | Any authenticated user could edit any topic |
| `deleteTopic` had no author/admin check | Any authenticated user could delete any topic |
| `updateTopic` didn't guard blank title after trim | Title could be set to whitespace-only |
| Frontend delete button visible to all users | Misleading — would fail server-side but confusing UX |
| Unused `Close` icon import in DiscussionPage | Lint warning |

## 2. What Was Fixed

### Backend — DiscussionService

Added `requireTopicAuthorOrAdmin(topic)` private method, called from both `updateTopic` and `deleteTopic`:
- Compares `currentUser` with `topic.createdBy`
- Falls back to `ROLE_ADMIN` check
- Throws `SecurityException` on mismatch

Added blank-title guard in `updateTopic`: if title is provided but trims to empty, throws `IllegalArgumentException`.

### Frontend — DiscussionPage

- Topic delete button now gated: `{(topic.createdBy === currentUsername || isAdmin) && ...}`
- Added `useAppSelector` + `authService` for current user context
- Removed unused `Close` icon import
- eslint: 0 warnings

### Tests — 4 new, 2 replaced

| Test | Description |
|------|-------------|
| `authorUpdatesTopic` | Author can update (replaces old `updatesTopic`) |
| `nonAuthorCannotUpdate` | Non-author non-admin rejected |
| `adminCanUpdate` | Admin can update any topic |
| `updateRejectsBlankTitle` | Blank title after trim rejected |
| `authorDeletesTopic` | Author can delete (replaces old `deletesTopic`) |
| `nonAuthorCannotDelete` | Non-author non-admin rejected |

Total: 13 tests (was 9).

## 3. Files Modified

| File | Change |
|------|--------|
| `service/DiscussionService.java` | +`requireTopicAuthorOrAdmin()`; updateTopic/deleteTopic now check auth; blank-title guard |
| `test/service/DiscussionServiceTest.java` | +4 new tests, 2 replaced with auth-aware versions |
| `pages/DiscussionPage.tsx` | +auth context; delete button gated by author/admin; removed unused Close import |

## 4. NOT Modified

All preview/rendition/search/ops-governance files untouched.
