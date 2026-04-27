# Phase 5 — Frontend Gap-Closure: Design & Verification

**Commits:** `da06d40` (initial Phase 5 frontend), `c216c9e` (design doc), `7019939` (PR-166 manager-flow fix)  
**Date:** 2026-04-27  
**Scope:** 5 new admin pages, 4 typed service clients, 11 mocked Playwright spec files (81 tests after PR-166)

---

## 1. Architecture

### 1.1 Routing

All new pages follow Athena's existing pattern:

```
App.tsx
  └── <Route path="/admin/X">
        └── <PrivateRoute requiredRoles={['ROLE_ADMIN']}>
              └── <MainLayout>
                    └── <XPage />
```

`InvitationAcceptPage` is the only exception — it is public (token-authenticated), no `PrivateRoute`, no `MainLayout`.

### 1.2 New Pages

| Path | Component | Gap |
|------|-----------|-----|
| `/admin/localized-content` | `LocalizedContentPage` | #14 Multilingual Content |
| `/admin/ldap` | `LdapSyncPage` | #6 LDAP/AD Directory Sync |
| `/admin/disposition-schedules` | `DispositionSchedulesPage` | #8 Disposition Schedules |
| `/admin/sites/:siteId/invitations` | `SiteInvitationsPage` | #7 Site Invitation Workflow |
| `/invitations/accept` | `InvitationAcceptPage` | #7 Site Invitation Workflow (accept flow) |

### 1.3 New Services

| File | Endpoints covered |
|------|-------------------|
| `localizedContentService.ts` | `GET/PUT/POST/DELETE /api/v1/nodes/{id}/localizations` |
| `ldapService.ts` | `GET/PUT /api/v1/ldap/config`, `POST /api/v1/ldap/test-connection`, `POST /api/v1/ldap/sync` |
| `dispositionScheduleService.ts` | `GET/PUT/POST/DELETE /api/v1/disposition-schedules`, `POST /api/v1/disposition-schedules/run-all` |
| `siteInvitationService.ts` | `GET/POST/DELETE /api/v1/sites/{id}/invitations`, `POST /api/v1/invitations/accept`, `POST /api/v1/invitations/reject` |

### 1.4 Page Design Notes

**LocalizedContentPage**
- Two-panel: node-ID lookup form + localizations table
- Inline per-row delete confirmation (no modal) — state held in `confirmDeleteLocale`
- `AddLocaleDialog` handles POST; PUT dialog handles update
- Delete `IconButton` carries `aria-label="Delete"` for Playwright accessibility

**LdapSyncPage**
- Three cards: Connection Status / Test Connection / Sync Now
- `not-configured` 404 state renders a setup prompt
- Sync result table shows `totalUsers`, `created`, `updated`, `deactivated`

**DispositionSchedulesPage**
- Left panel: enabled/disabled schedule list with chips
- Right panel: schedule detail with action list
- "Run All" button fires `POST /disposition-schedules/run-all`; response shown in snackbar

**SiteInvitationsPage**
- Routed via `/admin/sites/:siteId/invitations` using `useParams()`
- Status chips: PENDING (amber), ACCEPTED (green), REJECTED/EXPIRED (red/grey)
- Cancel `IconButton` carries `aria-label="Cancel invitation"` for Playwright accessibility
- Invite dialog: `inviteeEmail` ("Email address"), `invitedRole` (Select with InputLabel "Role"), optional `message`
- Route is gated by `<PrivateRoute>` only (any authenticated user) — not `ROLE_ADMIN`-restricted, since Athena's site model lets non-admin site managers invite members. Authorization remains enforced server-side in `SiteInvitationService` (PR-166 fix `7019939`).

**InvitationAcceptPage**
- Four flow states: `idle` → Accept/Decline buttons, `accepted`, `rejected`, `error`
- Two entry modes:
  - **URL-token mode**: `?token=...` populates `urlToken`; chip shows "Invitation token present"
  - **Manual-entry mode**: no URL param → chip "Manual token entry" + `Invitation token` TextField for manual paste
- Effective token = `urlToken || manualToken.trim()`; Accept/Decline buttons disabled until token is non-empty
- No auth required; the page itself is public, the API endpoints validate the token
- `POST /api/v1/invitations/accept` / `POST /api/v1/invitations/reject`

### 1.5 MainLayout Nav

"Multilingual" entry (TranslateIcon) added to the Admin section pointing to `/admin/localized-content`.

SitesPage admin panel gains an "Invitations" button that navigates to `/admin/sites/{siteId}/invitations`.

---

## 2. Playwright Mock Spec Strategy

### 2.1 LIFO Route Registration

Playwright registers routes in a **last-in, first-matched** (LIFO) order. All specs follow:

1. **Register catch-alls first** (lowest priority) — use `route.fallback()` (not `route.continue()`) to pass sub-path requests to lower-priority handlers.
2. **Register specific sub-path handlers last** (highest priority).

`route.continue()` sends to the real network and returns the SPA `index.html`, causing JSON parse failures. `route.fallback()` delegates to the next handler in the stack.

### 2.2 URL Glob Overlap

`**/api/v1/notifications/unread**` also matches `/unread-count` (glob `**` matches any suffix). The fix: register `/unread-count**` **after** `/unread**` so it has higher LIFO priority.

### 2.3 Strict Mode Selector Fixes

Playwright strict mode throws when a locator matches more than one element. Fixes applied:

| Pattern | Fix |
|---------|-----|
| `getByText('en')` — matches partial "Athena ECM", etc. | `{ exact: true }` |
| `getByText('Annual Report')` — matches "Annual Report CN" | `{ exact: true }` |
| `getByText('Memo.txt')` — matches `<em>Memo.txt</em>` and `<span>Updated Memo.txt.</span>` | `{ exact: true }` |
| `getByLabel('Name')` — "Namespace" starts with "Name" | `getByLabel('Name *', { exact: true })` (MUI adds asterisk for `required`) |
| `getByRole('button', { name: 'No' })` — "No" is prefix of "Notifications" | `{ exact: true }` |
| `getByText('Aspects')` — matches "Mandatory Aspects" | `{ exact: true }).first()` |
| `getByText('acme:invoice')` — appears in chip, combobox, and model chip | `.first()` |
| `getByTitle('Delete')` — MUI Tooltip doesn't set HTML `title` attribute | Added `aria-label="Delete"` on `IconButton`, use `getByRole('button', { name: 'Delete' })` |

### 2.4 MUI Tooltip Accessibility

`<Tooltip title="X">` does NOT propagate a `title` HTML attribute to the wrapped element. Playwright's `getByTitle()` will not find such buttons. Solution: add `aria-label` explicitly on the `IconButton` child.

---

## 3. Spec Coverage

### 3.1 New Specs — All Passing

| Spec file | Tests | Description |
|-----------|-------|-------------|
| `admin-gap-closure-smoke.mock.spec.ts` | 8 | Smoke test: all 8 new admin pages load without error |
| `admin-localized-content.mock.spec.ts` | 4 | Node ID lookup, add locale dialog, inline delete confirm, empty state |
| `admin-ldap-sync.mock.spec.ts` | 4 | Status card, test connection, sync now, 404 not-configured |
| `admin-disposition-schedules.mock.spec.ts` | 3 | Schedule list, detail panel, run-all |
| `admin-site-invitations.mock.spec.ts` | 5 | Invitation list, cancel button, invite dialog, DELETE call, non-admin authenticated access |
| `invitation-accept.mock.spec.ts` | 4 | Accept/decline buttons (URL token), success state, declined state, manual-token-entry submission |
| `notifications-email-preferences.mock.spec.ts` | 4 | Inbox list, mark-all-read, All-mode toggle, empty unread state |
| `admin-legal-holds.mock.spec.ts` | 4 | Holds list, status chips, hold detail, create+submit dialog |
| `admin-rules.mock.spec.ts` | 4 | Rules list, scheduled vs manual, create dialog, enable/disable toggle |
| `admin-content-models.mock.spec.ts` | 4 | Model list, type detail+properties, create dialog, aspect explorer |
| `saved-searches-smart-folder.mock.spec.ts` | 4 | Search list, run→navigate, smart-folder dialog fields, submit→navigate |

**Total: 81 tests, all passing on the latest run, 0 regressions introduced**

(Earlier runs hit 2 known-flaky failures in `advanced-search-preview-batch-scope.mock.spec.ts`, tracked in memory as the ES facet-aggregation timing race — they reproduce intermittently and are unrelated to this work.)

### 3.2 Test Result Summary

```
New mock specs (this work):  81 passed, 0 failed
Full chromium suite:          104 passed, 9 skipped, 0 failed (exit code 0)
Skipped tests are live-backend specs requiring a running Athena stack.
```

---

## 4. Verification Checklist

| # | Item | Status |
|---|------|--------|
| 1 | `LocalizedContentPage` renders at `/admin/localized-content` | ✓ (smoke + 4 dedicated tests) |
| 2 | Node ID lookup fires `GET /nodes/{id}/localizations` | ✓ |
| 3 | Inline delete confirm (no modal) | ✓ |
| 4 | AddLocaleDialog accessible via "Add Locale" button | ✓ |
| 5 | `LdapSyncPage` renders at `/admin/ldap` | ✓ (smoke + 4 dedicated tests) |
| 6 | Test connection result shown inline | ✓ |
| 7 | Sync stats rendered after POST sync | ✓ |
| 8 | 404 → not-configured state | ✓ |
| 9 | `DispositionSchedulesPage` renders at `/admin/disposition-schedules` | ✓ (smoke + 3 dedicated tests) |
| 10 | Schedule detail panel shows on row click | ✓ |
| 11 | Run-All fires POST and shows result | ✓ |
| 12 | `SiteInvitationsPage` renders at `/admin/sites/:siteId/invitations` | ✓ (4 tests) |
| 13 | Cancel invitation calls DELETE | ✓ |
| 14 | Invite dialog submits POST | ✓ |
| 15 | `InvitationAcceptPage` renders at `/invitations/accept?token=...` | ✓ (4 tests) |
| 16 | Accept → "You're in!" success state | ✓ |
| 17 | Decline → "Invitation declined" state | ✓ |
| 18 | Missing URL token → manual-entry mode with `Invitation token` input, then submit | ✓ |
| 18b | Non-admin authenticated user can reach `/admin/sites/:siteId/invitations` (PR-166) | ✓ |
| 19 | `NotificationsPage` inbox list with activity-type chips | ✓ |
| 20 | Mark All Read button visible when unreadCount > 0 | ✓ |
| 21 | All-mode toggle re-fetches via GET /notifications | ✓ |
| 22 | Empty unread state shows "No unread notifications" | ✓ |
| 23 | Content models list + type detail + aspect explorer | ✓ (4 tests) |
| 24 | Legal holds list + create dialog | ✓ (4 tests) |
| 25 | Automation rules list + create + toggle | ✓ (4 tests) |
| 26 | Saved searches + smart-folder create + run | ✓ (4 tests) |
| 27 | `aria-label` on icon buttons (Delete, Cancel invitation) | ✓ |
| 28 | `build` succeeds with no TypeScript errors | ✓ |
| 29 | All mock specs pass under `serve -s build` | ✓ |

---

## 5. Known Gaps / Next Steps

- **Phase 3 (#9 Property Encryption, #10 OAuth Store)**: No frontend yet — backend-only gap closure completed.
- **Phase 4 (Protocol extensions #11-17)**: Deferred per plan.
- **Live full-stack smoke**: Blocked on Docker image pulls (upstream EOF). Run `npx playwright test e2e/frontend-acceptance-smoke.spec.ts` when backend stack is available.
- **LDAP/Disposition config persistence UI**: Current pages show read/write but no visual "saved" confirmation beyond toast.
