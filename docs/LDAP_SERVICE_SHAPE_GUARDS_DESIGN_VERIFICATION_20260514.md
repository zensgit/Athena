# LDAP Service Shape Guards - Design and Verification

Date: 2026-05-14

## Context

This slice continues the frontend service response-shape guard closeout line.
`ldapService` backed the LDAP admin page but trusted `api.post<T>` responses
directly. A routed HTML fallback or malformed payload could therefore be treated
as a successful connection or sync result in mocked frontend tests.

## Backend Contract Evidence

`LdapSyncController` is mounted at `/api/v1/admin/ldap`, so the frontend API
base `/api/v1` makes the existing relative paths correct:

- `POST /admin/ldap/test-connection`
- `POST /admin/ldap/sync`

Backend records:

- `LdapConnectionStatus(boolean reachable, String userBaseDn, String groupBaseDn, String message)`
- `LdapSyncResult(String trigger, LocalDateTime syncedAt, int usersCreated, int usersUpdated, int usersDisabled, int usersSkipped, int groupsCreated, int groupsUpdated, int groupsDisabled, int groupsSkipped, int membershipsChanged, int unresolvedMembers, List<String> warnings)`

## Design

`ecm-frontend/src/services/ldapService.ts` now:

- Exports `LDAP_UNEXPECTED_RESPONSE_MESSAGE`.
- Calls `api.post<unknown>` and validates the response before returning typed DTOs.
- Rejects HTML fallback and malformed response bodies.
- Preserves existing method names, endpoint paths, payloads, and return types.

Guard rules:

- `LdapConnectionStatus.reachable` must be boolean.
- `userBaseDn`, `groupBaseDn`, and `message` may be string, `null`, or omitted.
- `LdapSyncResult.trigger` and `syncedAt` may be string, `null`, or omitted.
- All sync counters must be finite numbers.
- `warnings` must be an array of strings.

## Files Changed

- `ecm-frontend/src/services/ldapService.ts`
- `ecm-frontend/src/services/ldapService.test.ts`

## Verification

Targeted frontend verification:

```bash
cd ecm-frontend
npm test -- --runTestsByPath src/services/ldapService.test.ts --watchAll=false
```

Result will be recorded after integration verification.

Result: PASS. `ldapService.test.ts` ran 9 tests, 0 failures.

Full frontend gates:

```bash
cd ecm-frontend
npm run lint
CI=true npm run build
```

Result will be recorded after integration verification.

Result: PASS. `npm run lint` completed cleanly. `CI=true npm run build`
completed cleanly with the existing CRA bundle-size advisory.

## Residual Risk

This is a client-side response-shape guard only. It does not change LDAP sync
authorization, LDAP provider activation, or directory import semantics.
