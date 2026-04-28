# Security-Test Backfill — Index

A 6-round program ran 2026-04-27 → 2026-04-28 to backfill `@WebMvcTest`
security tests for legacy controllers. The thread is now closed. This
index points to the round-by-round design + verification docs so future
contributors can navigate them without scanning the full `docs/` tree.

## Round-by-round

| Round | Doc | Commit | Tests added | Cumulative repo total |
|---|---|---|---|---|
| Phase 5 close | `P5_BACKEND_SECURITY_TEST_FILL_20260427.md` | `799fd70` | NotificationController, EmailIntegrationController | 18 → 20 |
| 1 | `SECURITY_TEST_LEGACY_FILL_ROUND1_20260427.md` | `082e9cd` | MfaController, WebhookController, TenantAdminController | 20 → 23 |
| 2 | `SECURITY_TEST_LEGACY_FILL_ROUND2_20260427.md` | `3283ec5` | RuleController (3-tier auth) | 23 → 24 |
| 3 | `SECURITY_TEST_LEGACY_FILL_ROUND3_20260427.md` | `de72cfa` | UserController, GroupController, ShareLinkController (incl. permitAll redeem) | 24 → 25 |
| 4 | `SECURITY_TEST_LEGACY_FILL_ROUND4_20260427.md` | `1d53933` | TrashController, BulkOperationController, BulkImportController | 25 → 28 |
| 5 | `SECURITY_TEST_LEGACY_FILL_ROUND5_20260427.md` | `7dbfc91` | ScriptController (RCE), PermissionTemplateController | 28 → 30 |
| 6 + closeout | `SECURITY_TEST_LEGACY_FILL_ROUND6_AND_THREAD_CLOSEOUT_20260428.md` | `4c66a7e` | LicenseController, SecurityController | 30 → 32 |

Plus two CI-polish commits during the thread:
- `c542a1e` — concrete `Page<T>` generics, `PageRequest.of(...)`, real method mocks for rounds 2–5
- `9e697a8` — Phase 5 mocked gate navigation stabilization

## Cumulative outcome

- **+14 security tests, –14 untested controllers** across the thread
- Untested legacy controllers: 56 → 42
- Every Phase-5 controller has parity (functional + security tests)
- High-blast-radius surfaces covered: auth primitives, identity, public
  shares, multi-tenant, automation, mutation amplifiers, control planes,
  per-node ACLs

## What's in each doc

Each round doc has the same shape:
1. **Why this round** — the criteria for picking these specific controllers
2. **Design** — gate model, load-bearing assertions, sampling strategy
3. **Verification** — local-blocked-by-Docker note + CI checklist
4. **After this commit** — running tally and what's flagged for next round

The closeout doc (round 6) additionally has §4 covering:
- §4.1 What's covered (by category)
- §4.2 What's deliberately not covered (3 buckets: per-node content,
  read-mostly metadata, protocol endpoints) and why
- §4.3 The established `@WebMvcTest` template for future contributors
- §4.4 Recommendation against another speculative round

## How to add a new security test

When adding a new controller, copy any existing `*SecurityTest.java` as a
template. The closeout doc §4.3 lists required calibrations. CLAUDE.md
also surfaces this so a fresh Claude session finds the pattern on
conversation start.

## How NOT to use this thread

- **Do not run another speculative round** picking arbitrary unprotected
  controllers. The marginal value drops sharply once high-blast-radius
  surfaces are covered, and the test pattern is now well-established for
  any contributor to follow.
- **Do not test protocol endpoints** (CMIS, WOPI, Transfer) with the
  `@WithMockUser` pattern — they have non-standard auth (basic auth,
  access tokens, per-job tokens) that warrants its own test design.
