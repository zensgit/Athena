# Site Invitations — Gap #7 Backend (design + verification)

## Date
2026-04-26

## Status
Backend implementation complete. Commit `33a8256` pushed to `origin/main`.
Closes Gap #7 (token-based site invitation workflow) at the backend layer.
Frontend UI deferred to a follow-up PR.

---

## Gap context

The existing `site_membership_requests` table (migration 074) models a
**user-initiated** join request: an existing user asks to join a site, and a
manager approves or rejects. Gap #7 is a different flow: a **manager or admin
invites an email address** to a site; the invitee receives a link, clicks
"Accept", and is added as a member — optionally before they log in.

---

## Files added

| File | Description |
|------|-------------|
| `ecm-core/src/main/resources/db/changelog/changes/086-create-site-invitations.xml` | Migration |
| `ecm-core/src/main/java/com/ecm/core/entity/SiteInvitation.java` | Entity |
| `ecm-core/src/main/java/com/ecm/core/repository/SiteInvitationRepository.java` | Repository |
| `ecm-core/src/main/java/com/ecm/core/service/SiteInvitationService.java` | Service |
| `ecm-core/src/main/java/com/ecm/core/controller/SiteInvitationController.java` | Controller |
| `db.changelog-master.xml` | +086 registration |

---

## Migration 086 — `site_invitations`

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID PK | gen_random_uuid() |
| `site_id` | UUID FK → sites(id) | ON DELETE CASCADE |
| `invitee_email` | varchar(255) NOT NULL | normalized to lowercase |
| `invitee_username` | varchar(150) | set at accept-time |
| `invited_role` | varchar(50) DEFAULT 'CONSUMER' | MANAGER / COLLABORATOR / CONSUMER |
| `token` | varchar(64) UNIQUE NOT NULL | 32-byte SecureRandom hex |
| `status` | varchar(20) DEFAULT 'PENDING' | PENDING / ACCEPTED / REJECTED / EXPIRED / CANCELLED |
| `message` | text | optional note from inviter |
| `invited_by` | varchar(150) NOT NULL | username of the inviter |
| `expires_at` | timestamp NOT NULL | now + 7 days |
| `accepted_at` | timestamp | set at accept-time |
| `created_date` | timestamp NOT NULL | default now |
| `last_modified_date` | timestamp | JPA managed |

Indexes: `(site_id, status)` and `(invitee_email, status)`.

---

## Entity — `SiteInvitation extends BaseEntity`

```java
@ManyToOne(fetch = LAZY) @JoinColumn(name = "site_id") Site site;
String inviteeEmail;
String inviteeUsername;       // null until accepted
String invitedRole;           // stored as enum name string
String token;                 // 64-char hex (unique)
Status status;                // inner enum
String message;
String invitedBy;
LocalDateTime expiresAt;
LocalDateTime acceptedAt;
enum Status { PENDING, ACCEPTED, REJECTED, EXPIRED, CANCELLED }
```

---

## Service — `SiteInvitationService`

### `invite(siteId, InviteRequest)` → `SiteInvitationDto`
- Requires ROLE_ADMIN or site MANAGER role
- Validates non-blank `inviteeEmail`; normalizes to lowercase
- Rejects if a PENDING invitation already exists for this email+site
- Generates a 32-byte `SecureRandom` token (64-char hex)
- `expiresAt = now + 7 days`
- Saves, then attempts email delivery via `ObjectProvider<EmailNotificationService>`
  (fire-and-forget; failure logged as WARN, never thrown)

### `accept(token)` → `SiteInvitationDto`
- Looks up invitation by token; verifies PENDING + not-expired
- If invitee is already a site member: marks ACCEPTED without re-adding (idempotent)
- Otherwise creates `SiteMember` directly via `SiteMemberRepository`
- Fires `activityEventListener.postSiteMemberActivity("site.member.added", ...)`
- Sets `status=ACCEPTED`, `acceptedAt=now`, `inviteeUsername=currentUser`

### `reject(token)` → `SiteInvitationDto`
- Verifies PENDING; sets `status=REJECTED`

### `cancel(siteId, invitationId)`
- Requires ROLE_ADMIN or site MANAGER; verifies PENDING; sets `status=CANCELLED`

### `listForSite(siteId)` → `List<SiteInvitationDto>`
- Requires ROLE_ADMIN or site MANAGER
- Returns all invitations ordered by `createdDate DESC`

### `cleanupExpired()` — `@Scheduled(cron = "0 0 3 * * *")`
- Bulk-updates PENDING invitations past `expires_at` → EXPIRED
- Runs daily at 3am; no-op when empty

---

## Controller — `SiteInvitationController`

### Site-scoped (manager / admin)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/sites/{siteId}/invitations` | List invitations |
| POST | `/api/v1/sites/{siteId}/invitations` | Create invitation body: `{ inviteeEmail, invitedRole?, message? }` |
| DELETE | `/api/v1/sites/{siteId}/invitations/{invitationId}` | Cancel invitation |

### Token-based (any authenticated user)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/invitations/accept` | Accept: `{ token }` |
| POST | `/api/v1/invitations/reject` | Reject: `{ token }` |

All endpoints: `@PreAuthorize("isAuthenticated()")`. Business-level permission
checks are enforced in the service layer.

---

## Email template (deferred)

`sendInvitationEmail` fires against `EmailNotificationService` with template
key `"site.invitation"`. No seed row exists yet in `email_template` — the service
logs WARN and returns if the template is not found. A follow-up migration can
add the template without any code change.

---

## Security

- Token is 256-bit SecureRandom hex — brute-force infeasible
- `expiresAt = now + 7 days` — stale links auto-expire via nightly cleanup
- Accept-time duplicate guard prevents double-membership
- Permission checks at service layer (ROLE_ADMIN or site MANAGER)
- Cascade DELETE: invitations are purged when the site is deleted

---

## Compilation

```bash
./mvnw compile -pl ecm-core -q
# → (no output — clean compile)
```

---

## Non-goals (deferred to follow-up PRs)

- Frontend UI for sending invitations and viewing pending/accepted state
- Email template seed row for `site.invitation`
- Unauthenticated accept flow (deep-link before login — requires custom auth bypass)
- Invitation resend / bump expiry endpoint
