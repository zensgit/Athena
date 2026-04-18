# P3 PR-13 LDAP Sync Design

## Date
- 2026-04-14

## Scope
- Add a first-class LDAP / Active Directory mirror for:
  - `users`
  - `groups`
  - `user_groups`
- Keep site membership out of the first slice.
- Keep Athena's existing local database as the authority source for:
  - people lookup
  - group membership expansion
  - ACL authority resolution

## Core Decisions
- `ecm.identity.provider=ldap` activates a dedicated `LdapUserGroupBackend`.
- LDAP mode is read-through-local-mirror, not remote-read-on-every-request.
- Direct user/group mutation APIs are rejected in LDAP mode.
- Sync uses conservative semantics:
  - create or update mirrored users/groups
  - converge memberships
  - disable missing principals instead of hard delete
- Username and group-name renames are not auto-applied once an LDAP identity is already mirrored locally.
  - This preserves Athena authority keys and avoids breaking workflow/site/string-backed references.

## Schema
- New Liquibase change:
  - `076-add-ldap-directory-columns.xml`
- Added to `users` and `groups`:
  - `directory_managed`
  - `directory_source`
  - `directory_external_id`
  - `directory_dn`
  - `directory_last_synced_at`
- Added unique identity constraints on:
  - `(directory_source, directory_external_id)`

## Runtime Components
- `integration/ldap/LdapSyncProperties`
  - env-backed LDAP connection, attribute-mapping, and scheduler config
- `integration/ldap/JndiLdapDirectoryClient`
  - JNDI bind/search client
  - supports connection test and snapshot fetch
- `integration/ldap/LdapSyncService`
  - upsert users/groups
  - deterministic membership convergence
  - disable-missing behavior
  - `authorities` cache eviction on sync
- `integration/ldap/LdapSyncScheduler`
  - scheduled sync via `ecm.ldap.sync.*`
- `controller/LdapSyncController`
  - `POST /api/v1/admin/ldap/test-connection`
  - `POST /api/v1/admin/ldap/sync`
- `service/LdapUserGroupBackend`
  - local-mirror read backend for `ldap` provider
  - explicit rejection of direct mutations

## Safety Notes
- Membership sync writes the owning side (`User.groups`) and the inverse side together.
- Missing directory users are disabled and detached from directory-managed groups.
- Missing directory groups are disabled and their memberships are cleared.
- Duplicate username/name collisions are skipped with warnings instead of mutating existing local principals.
- Duplicate/missing email is handled conservatively:
  - use directory email when unique
  - fall back to synthetic `@ldap.local` email when needed
  - skip only when no safe unique email can be assigned

## Config Surface
- Added `ecm.ldap.*` in `application.yml`
- Important vars:
  - `ECM_IDENTITY_PROVIDER=ldap`
  - `ECM_LDAP_URL`
  - `ECM_LDAP_BIND_DN`
  - `ECM_LDAP_BIND_PASSWORD`
  - `ECM_LDAP_USER_BASE_DN`
  - `ECM_LDAP_GROUP_BASE_DN`
  - `ECM_LDAP_SYNC_ENABLED`
  - `ECM_LDAP_SYNC_CRON`

## Deferred
- Nested group expansion
- modifyTimestamp/delta checkpoint sync
- LDAP-backed authentication
- site membership automation from directory groups
- admin UI beyond the two backend endpoints
