# LDAP Directory Sync Admin UI — Gap #6 Frontend Closeout (design + verification)

## Date
2026-04-26

## Status
Frontend implementation complete. Closes the frontend gap for Gap #6 (LDAP/AD Directory Sync).
Backend was already complete (`LdapSyncController`, `LdapSyncService`).

---

## Scope

Provide an admin utility page for testing the LDAP connection and triggering a manual sync.
The backend is `@ConditionalOnProperty(name = "ecm.identity.provider", havingValue = "ldap")`
so the page must degrade gracefully when LDAP is not configured.

---

## Files added / modified

| File | Change |
|------|--------|
| `ecm-frontend/src/services/ldapService.ts` | new — typed API client |
| `ecm-frontend/src/pages/LdapSyncPage.tsx` | new — admin utility page |
| `ecm-frontend/src/App.tsx` | + route `/admin/ldap` (ROLE_ADMIN) |
| `ecm-frontend/src/components/layout/MainLayout.tsx` | + nav entry "Directory Sync" |

---

## API client — `ldapService.ts`

Class-instance singleton following the `legalHoldService` pattern.

```typescript
LdapConnectionStatus {
  reachable: boolean;
  message: string;
  userBaseDn?: string;
  groupBaseDn?: string;
}

LdapSyncResult {
  usersCreated: number; usersUpdated: number;
  usersDisabled: number; usersSkipped: number;
  groupsCreated: number; groupsUpdated: number;
  groupsDisabled: number; groupsSkipped: number;
  membershipsChanged: number; unresolvedMembers: number;
  warnings: string[];
  syncedAt: string;
  trigger: string;
}
```

Two methods:
- `testConnection()` → `POST /api/v1/admin/ldap/test-connection` → `LdapConnectionStatus`
- `syncNow()` → `POST /api/v1/admin/ldap/sync` → `LdapSyncResult`

Errors propagate to the caller; the page handles 404/503 as "LDAP not configured".

---

## Page design — `LdapSyncPage.tsx`

Route: `/admin/ldap`

Two MUI `Card` components (not master-detail — this is a utility page):

### Connection Status card
- "Test Connection" button with `CircularProgress` during execution
- On success: reachability chip (green=reachable / red=unreachable), `userBaseDn`,
  `groupBaseDn`, `message` field
- On error: `toast.error`

### Directory Sync card
- "Sync Now" button with `CircularProgress` during execution
- On success: stats broken into three chip-row groups:
  - Users: created / updated / disabled / skipped
  - Groups: created / updated / disabled / skipped
  - Memberships: changed / unresolved
- Warnings list rendered as amber Alert if `warnings.length > 0`
- `syncedAt` and `trigger` shown in caption below stats
- On error: `toast.error`

### LDAP-not-configured state
If either operation returns HTTP 404 or 503, a shared `ldapNotConfigured` flag is
set to `true` and an informational `Alert` replaces both cards:

> LDAP integration is not enabled for this instance. Set `ecm.identity.provider=ldap`
> to activate directory sync.

---

## Routing and navigation

Route: `/admin/ldap` — `ROLE_ADMIN` required.

Nav: "Directory Sync" added to MainLayout admin menu after "Legal Holds",
using `ManageAccounts` icon (not used elsewhere — distinct from `SyncAlt`
used by Transfer Replication and `People` used by People Directory).

---

## TypeScript verification

```
cd ecm-frontend && npx tsc --noEmit 2>&1 | grep -v node_modules
# → (no output — zero source-file errors)
```

---

## Backend API reference (already shipped)

| Endpoint | Auth | Description |
|----------|------|-------------|
| `POST /api/v1/admin/ldap/test-connection` | ROLE_ADMIN | Test LDAP connectivity |
| `POST /api/v1/admin/ldap/sync` | ROLE_ADMIN | Trigger manual directory sync |

Both endpoints are `@ConditionalOnProperty(name = "ecm.identity.provider", havingValue = "ldap")`.

---

## Non-goals (deferred)

- Scheduled sync configuration UI (cron expression editor) — backend `@Scheduled` is hardcoded
- Per-group or per-OU sync scope selection
- Sync history log page
