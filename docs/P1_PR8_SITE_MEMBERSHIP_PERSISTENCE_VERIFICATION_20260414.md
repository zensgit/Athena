# P1 PR-8 Site Membership Persistence Verification

## Date
- 2026-04-14

## Status
- Automated verification passed

## Targeted Verification

### Command
```bash
cd ecm-core
./mvnw -B -Dstyle.color=never test -Dtest=SiteMembershipServiceTest,SiteMemberRosterTest,PeopleControllerTest,PeopleControllerSecurityTest,SiteControllerTest,SiteMembershipContractTest
```

### Result
- `BUILD SUCCESS`
- `Tests run: 76, Failures: 0, Errors: 0, Skipped: 0`

## Full Backend Verification

### Command
```bash
cd ecm-core
./mvnw -B -Dstyle.color=never test
```

### Result
- `BUILD SUCCESS`
- `Tests run: 1414, Failures: 0, Errors: 0, Skipped: 11`

## Static Verification

### Command
```bash
git diff --check
```

### Result
- passed

## Verified Behaviors
- `PeopleController` site membership endpoints delegate to `SiteMembershipService`
- request create/update/approve/reject/withdraw are DB-first
- duplicate request creation is blocked across persistent rows and legacy compatibility data
- legacy JSONB rows remain readable during the compatibility window
- legacy rows can be materialized into first-class persistence on mutation paths
- site `MANAGER` can add/remove members and change roles without admin-only controller guards
- moderation queue authorization failures surface as `403`

## Test Classes
- `SiteMembershipServiceTest`
- `SiteMemberRosterTest`
- `PeopleControllerTest`
- `PeopleControllerSecurityTest`
- `SiteControllerTest`
- `SiteMembershipContractTest`
- full backend regression suite

## Migration Note
- `074-create-site-membership-requests.xml` is included from `db.changelog-master.xml`.
- This turn did not run a standalone migrated-data Liquibase smoke against a non-empty upgrade database.
- A staged upgrade rehearsal is still recommended before production rollout.

## Merge Decision
- `PR-8 approve`
