# Site Invitations — Gap #7 Frontend Closeout (design + verification)

## Date
2026-04-26

## Status
Frontend implementation complete. Closes Gap #7 frontend layer.
Backend was already complete (commit `33a8256`). Backend design at
`docs/P5_SITE_INVITATIONS_VERIFICATION_20260426.md`.

---

## Scope

Provide a manager/admin UI for sending and managing site invitations,
plus a token-based accept/reject landing page for invited users.

---

## Files added / modified

| File | Change |
|------|--------|
| `ecm-frontend/src/services/siteInvitationService.ts` | new — typed API client |
| `ecm-frontend/src/pages/SiteInvitationsPage.tsx` | new — manager invitation list page |
| `ecm-frontend/src/pages/InvitationAcceptPage.tsx` | new — token-based accept/reject landing |
| `ecm-frontend/src/App.tsx` | + 2 routes |
| `ecm-frontend/src/pages/SitesPage.tsx` | + "Invitations" button in site detail panel |

---

## API client — `siteInvitationService.ts`

Class-instance singleton following the `legalHoldService` pattern.

```typescript
// Exported types
SiteInvitationDto {
  id: string; siteId: string; siteTitle: string;
  inviteeEmail: string; inviteeUsername: string | null;
  invitedRole: string; status: string; message: string | null;
  invitedBy: string; expiresAt: string; acceptedAt: string | null;
  createdDate: string;
}
InviteRequest { inviteeEmail: string; invitedRole?: string; message?: string }
TokenRequest  { token: string }
```

Five methods:
- `listInvitations(siteId)` → `GET /api/v1/sites/{siteId}/invitations`
- `createInvitation(siteId, data)` → `POST /api/v1/sites/{siteId}/invitations`
- `cancelInvitation(siteId, invitationId)` → `DELETE /api/v1/sites/{siteId}/invitations/{id}` (void / 204)
- `acceptInvitation(data)` → `POST /api/v1/invitations/accept`
- `rejectInvitation(data)` → `POST /api/v1/invitations/reject`

---

## Page design — `SiteInvitationsPage.tsx`

Route: `/admin/sites/:siteId/invitations`

Invitation table columns:
- Email (+ accepted username in parentheses when set)
- Role chip
- Status chip: `PENDING`=warning/amber, `ACCEPTED`=success/green, `REJECTED`/`EXPIRED`/`CANCELLED`=default/grey
- Invited by
- Expires at (formatted)
- Cancel action button (PENDING rows only)

"Invite" button opens `CreateInvitationDialog` (embedded sub-component):
- inviteeEmail (required text field)
- invitedRole Select: CONSUMER (default) / COLLABORATOR / MANAGER
- message (optional multiline)

State management: table refreshes on invite/cancel via full reload of `listInvitations`.

---

## Page design — `InvitationAcceptPage.tsx`

Route: `/invitations/accept` (authenticated, no role restriction)

Reads `?token=` from `useSearchParams()`. Action-first UX since no
GET-by-token endpoint exists in the backend:

1. Page renders with "Accept" and "Decline" buttons and explanatory text
2. On "Accept": calls `acceptInvitation({ token })` → shows `SiteInvitationDto` details (site title, role)
3. On "Decline": calls `rejectInvitation({ token })` → shows declined confirmation
4. Error states: missing token (no query param), expired/not-found (API error), already used

On success renders a link back to the site or home.

---

## Routing

| Route | Component | Guard |
|-------|-----------|-------|
| `/admin/sites/:siteId/invitations` | `SiteInvitationsPage` | `ROLE_ADMIN` |
| `/invitations/accept` | `InvitationAcceptPage` | `isAuthenticated` (no role) |

### Guard rationale
`ROLE_ADMIN`-only for `SiteInvitationsPage`: site-level manager is
`SiteMemberRole.MANAGER` (a JPA enum), not a Spring Security role. The
backend enforces site-level access; the frontend guard keeps
unauthenticated/non-admin users out. Site managers reach invitations via
the Sites page "Invitations" button as an admin-delegated action.

### Entry point — `SitesPage.tsx`
An "Invitations" button was added to the site-detail member panel header
(admin-only, navigates to `/admin/sites/${selectedSiteId}/invitations`).

---

## TypeScript verification

```
cd ecm-frontend && npx tsc --noEmit 2>&1 | grep -v node_modules
# → (no output — zero source-file errors)
```

---

## Non-goals (deferred)

- "Pending invitations for me" inbox view — users accept via the email link
- Unauthenticated accept flow (deep-link before login — requires custom auth bypass)
- Invitation resend / bump expiry endpoint
- `site.invitation` email template seed row (logs WARN until added)
