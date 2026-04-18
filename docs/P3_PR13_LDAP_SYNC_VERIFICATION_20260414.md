# P3 PR-13 LDAP Sync Verification

## Date
- 2026-04-14

## Result
- `PR-13 approve`

## Targeted Backend Verification

```bash
cd ecm-core
./mvnw -B -Dstyle.color=never test \
  -Dtest=LdapSyncServiceTest,LdapSyncControllerTest,LdapSyncControllerSecurityTest,LdapUserGroupBackendTest
```

### Outcome
- `BUILD SUCCESS`
- `Tests run: 10, Failures: 0, Errors: 0, Skipped: 0`

## Full Backend Regression

```bash
cd ecm-core
./mvnw -B -Dstyle.color=never test
```

### Outcome
- `BUILD SUCCESS`
- `Tests run: 1456, Failures: 0, Errors: 0, Skipped: 11`

## Verified Behaviors
- LDAP sync creates mirrored users, groups, and memberships.
- Missing mirrored users/groups are disabled rather than deleted.
- Membership convergence is repeatable and deterministic.
- Existing local authority keys survive directory rename attempts.
- LDAP admin endpoints require authentication and `ROLE_ADMIN`.
- `ldap` provider exposes local-mirror reads and rejects direct writes.

## Static Checks

```bash
git diff --check
```

### Outcome
- passed
