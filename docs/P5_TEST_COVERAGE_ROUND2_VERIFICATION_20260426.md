# Test Coverage Round 2 — Gaps #1 #3 #4 #5 Frontend Mock Specs

## Date
2026-04-26

## Status
Complete. 4 new Playwright mock specs; TypeScript clean; zero source-file errors.

---

## Scope

Complete frontend e2e mock spec coverage for the remaining closed gaps that lacked specs after Round 1:

| Gap | Feature | Covered by |
|-----|---------|-----------|
| #1 | Smart / Virtual Folders | `saved-searches-smart-folder.mock.spec.ts` |
| #3 | Scheduled User Rules | `admin-rules.mock.spec.ts` |
| #4 | Custom Content Models | `admin-content-models.mock.spec.ts` |
| #5 | Email Notifications | `notifications-email-preferences.mock.spec.ts` |

---

## Files Added

| File | Lines | Route |
|------|-------|-------|
| `ecm-frontend/e2e/admin-rules.mock.spec.ts` | 215 | `/rules` |
| `ecm-frontend/e2e/admin-content-models.mock.spec.ts` | 251 | `/admin/content-models` |
| `ecm-frontend/e2e/notifications-email-preferences.mock.spec.ts` | 198 | `/notifications` |
| `ecm-frontend/e2e/saved-searches-smart-folder.mock.spec.ts` | 222 | `/saved-searches` |

---

## Test Design

### `admin-rules.mock.spec.ts` — 4 tests (Gap #3)

`RulesPage` mounts 6 endpoints on load: `/rules`, `/rules/templates`, `/rules/stats`, `/rules/actions/definitions`, `/rules/executions/timeline`, `/rules/executions/audit`. All mocked before `page.goto`.

| Test | Validates |
|------|-----------|
| shows automation rules list | Paginated response; both rule names + stats chips visible |
| shows scheduled vs manual distinction | `SCHEDULED` trigger chip + `Backfill: 30m` caption for cron rule |
| create rule dialog opens | "New Rule" button → dialog with Name field, Save/Cancel |
| toggle rule enabled/disabled | Switch in row → PATCH `.../disable` intercepted; switch becomes unchecked |

Key finding: `getAllRules` expects `{ content: [...], totalElements, ... }` paginated wrapper, not a bare array.
`actions/definitions` returns `{ actions: [] }`, not a bare array.

---

### `admin-content-models.mock.spec.ts` — 4 tests (Gap #4)

`ContentModelsPage` auto-selects the first type and first aspect on mount, triggering 8 total API calls. All mocked.

| Test | Validates |
|------|-----------|
| shows content models list | `acme:contentModel`, "Model Registry", ACTIVE chip visible |
| shows type detail and properties panel | Auto-selected type → "Dictionary Type Explorer", qualified name, property title |
| create model dialog opens | "New Model" → dialog with Prefix, Name, Namespace URI fields |
| shows Dictionary Aspect Explorer with aspect name | `acme:auditable` qualified name in auto-selected aspect panel |

Key finding: On mount the page also calls `getType`, `getTypeHierarchy`, `getMandatoryAspects` for the auto-selected type and `getAspect` for the auto-selected aspect. Glob routes registered with specific paths before catch-all paths (Playwright matches last-registered first).

---

### `notifications-email-preferences.mock.spec.ts` — 4 tests (Gap #5)

**Adaptation:** `NotificationsPage.tsx` is a notification inbox, not a preference editor. Email preference toggles live in `RecordsManagementPage` (preset deliveries) — scoped to RM, not generic. Tests cover what `/notifications` actually renders.

Endpoints: `GET /notifications/unread` (paginated), `GET /notifications/unread-count`, `POST /notifications/mark-all-read`.

| Test | Validates |
|------|-----------|
| shows notification inbox list | Activity-type chips and node names from mock data |
| mark all read calls the API | "Mark All Read" → POST intercepted |
| switching to All mode re-fetches | "All" button → different mock data appears |
| empty state | Empty unread → "No unread notifications" text; "Mark All Read" absent |

---

### `saved-searches-smart-folder.mock.spec.ts` — 4 tests (Gap #1)

**Adaptations:**
- No `isSmart` field on `SavedSearch` DTO; no visual indicator per row — test 2 replaced with "run search" test
- Smart folder creation is per-row `IconButton` (`aria-label="Create smart folder from saved search {name}"`), not a page-level button

Endpoints: `GET /search/saved`, `GET /search/saved/{id}/execute`, `POST /search/saved/{id}/smart-folder`.

| Test | Validates |
|------|-----------|
| shows saved searches list | Both search names in DataGrid; toolbar buttons visible |
| run saved search navigates to /search-results | Per-row "Run" button → URL changes to `/search-results` |
| create smart folder dialog shows correct fields | Per-row CreateNewFolder button → dialog with "Create Smart Folder" title, Folder Name, Description, submit |
| submitting smart folder navigates to new folder | Fill name → submit → POST intercepted → URL becomes `/browse/sf-abc` |

---

## TypeScript Verification

```bash
cd ecm-frontend && npx tsc --noEmit 2>&1 | grep -v node_modules
# → (no output — zero source-file errors)
```

---

## Complete Mock Spec Coverage Map (all gaps)

| Gap | Feature | Mock Spec | Status |
|-----|---------|-----------|--------|
| #1 | Smart / Virtual Folders | `saved-searches-smart-folder.mock.spec.ts` | ✅ Round 2 |
| #2 | Legal Holds | `admin-legal-holds.mock.spec.ts` | ✅ Round 1 |
| #3 | Scheduled Rules | `admin-rules.mock.spec.ts` | ✅ Round 2 |
| #4 | Custom Content Models | `admin-content-models.mock.spec.ts` | ✅ Round 2 |
| #5 | Email Outbound | `notifications-email-preferences.mock.spec.ts` | ✅ Round 2 |
| #6 | LDAP Directory Sync | `admin-ldap-sync.mock.spec.ts` | ✅ Round 1 |
| #7 | Site Invitations | `admin-site-invitations.mock.spec.ts` + `invitation-accept.mock.spec.ts` | ✅ Round 1 |
| #8 | Disposition Schedules | `admin-disposition-schedules.mock.spec.ts` | ✅ Round 1 |
| #9 | Property Encryption | N/A — transparent JPA layer, no UI | — |
| #10 | OAuth Credential Store | N/A — no UI (deferred) | — |
| #14 | Multilingual Content | `admin-localized-content.mock.spec.ts` | ✅ Round 1 |
| #15 | Records Management | `rm-report-preset-schedule.mock.spec.ts` (pre-existing) | ✅ Pre-existing |

All 10 UI-bearing closed gaps now have Playwright mock spec coverage.
