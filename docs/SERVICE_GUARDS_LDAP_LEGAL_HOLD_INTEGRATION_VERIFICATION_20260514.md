# Service Guards: LDAP + Legal Hold Integration Verification

Date: 2026-05-14

## Scope

This round continued the frontend service response-shape guard closeout line.
It hardened two small admin-facing services that still trusted typed API
responses directly:

- `ldapService`
- `legalHoldService`

Both slices preserve their existing public APIs, endpoint paths, request
payloads, and return types.

## Parallel Development Split

Codex implemented and verified the LDAP slice in the main worktree:

- `ecm-frontend/src/services/ldapService.ts`
- `ecm-frontend/src/services/ldapService.test.ts`
- `docs/LDAP_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260514.md`

Claude implemented the legal hold slice in an isolated worktree:

- Worktree: `.claude/worktrees/claude-legal-hold-service-guards`
- Branch: `worktree-claude-legal-hold-service-guards`
- Integrated commit: `58b2cca`

Codex reviewed Claude's diff against the backend contract, reran verification,
fixed one TypeScript build issue in `isLegalHoldDetail`, updated the
verification doc with real results, committed the worktree, and cherry-picked it
back to `main`.

## Backend Contract Checks

LDAP:

- Backend controller: `LdapSyncController`
- Mount: `/api/v1/admin/ldap`
- Frontend relative paths remain `/admin/ldap/test-connection` and `/admin/ldap/sync`
- Response shapes: `LdapConnectionStatus` and `LdapSyncResult`

Legal hold:

- Backend controller: `LegalHoldController`
- Mount: `/api/v1/legal-holds`
- Frontend relative path remains `/legal-holds`
- `listHolds` response shape: `List<LegalHoldSummaryDto>`
- Detail and mutation response shape: `LegalHoldDto`

## Guard Rules Added

LDAP:

- Rejects HTML fallback and malformed responses.
- Requires `reachable` to be boolean.
- Accepts nullable or omitted `userBaseDn`, `groupBaseDn`, `message`, `trigger`, and `syncedAt`.
- Requires all sync counters to be finite numbers.
- Requires `warnings` to be an array of strings.

Legal hold:

- Rejects HTML fallback, page envelopes for list, malformed summaries, and malformed detail readbacks.
- Validates status against `ACTIVE` and `RELEASED`.
- Requires summary `id`, `name`, `status`, and numeric `itemCount`.
- Requires detail `items` array and item `nodeId`.
- Accepts nullable or omitted descriptive and audit strings.

## Verification

Targeted service tests after integration:

```bash
cd ecm-frontend
npm test -- --runTestsByPath src/services/ldapService.test.ts src/services/legalHoldService.test.ts --watchAll=false
```

Result: PASS. 2 test suites, 28 tests, 0 failures.

Frontend lint:

```bash
cd ecm-frontend
npm run lint
```

Result: PASS.

Frontend production build:

```bash
cd ecm-frontend
CI=true npm run build
```

Result: PASS. CRA emitted the existing bundle-size advisory.

## Commits

- `86d7c51 fix(ldap): guard service responses`
- `58b2cca fix(legal-holds): guard service responses`

## Notes

The Claude split remained useful for parallel implementation, but Codex still
owned cross-boundary review and final verification. Claude's session wrote the
legal hold files but could not run `npm` or `git` commands. Codex temporarily
reused the main worktree's `node_modules` in the Claude worktree, removed the
symlink before staging, and performed the final commit/cherry-pick.

The main worktree still has the pre-existing local `.env` modification. It was
not staged or changed by this round.
