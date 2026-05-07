# OAuth Credential Revoke Capability Metadata - Integration Verification

Date: 2026-05-07

## Context

This round integrates Claude's three-package OAuth Provider Revoke capability-metadata refactor into `main`:

- Package A: `claude/oauth-revoke-meta-backend` / `5e68702` adds backend capability fields and tests.
- Package B: `claude/oauth-revoke-meta-ui` / `4a8fd87` makes the UI consume backend capability metadata instead of hard-coding `provider === 'GOOGLE'`.
- Package C: `claude/oauth-revoke-meta-docs` / `a8adb2b` adds closeout and Microsoft/CUSTOM follow-up docs.

The integration landed on `main` as:

- `0a71a40 feat(admin): expose OAuth provider-revoke capability metadata`
- `e6184de feat(admin): drive OAuth Provider Revoke button from capability metadata`
- `e208cce docs(admin): refresh OAuth revoke closeout TODO + Microsoft/CUSTOM follow-up`

## Design Review

The important integration check was cross-boundary consistency: the frontend must not display a Provider Revoke action for rows that `POST /api/v1/admin/oauth-credentials/{id}/revoke` would reject.

The merged design uses the backend as the single source of truth:

- `OAuthCredentialInventoryItem` now includes `providerRevokeSupported` and `providerRevokeUnsupportedReason`.
- `OAuthCredentialAdminService.withCapability(...)` enriches repository projections after query time and uses the same branches and messages as `OAuthCredentialService.revokeProviderTokens(...)`.
- The repository projection intentionally returns capability defaults `(false, null)` so JPQL stays redaction-only and the service remains the capability decision point.
- The React admin page disables Provider Revoke based on `providerRevokeSupported`, not provider name or local token flags reconstructed on the client.
- Unsupported rows surface the backend-supplied reason on the disabled wrapper, avoiding a silent disabled button.

No database migration was required. The API response is additive and token-redacted.

## Verification

Local checks run after cherry-picking A -> B -> C:

| Scope | Command | Result |
| --- | --- | --- |
| Backend targeted, including persistence projection | `cd ecm-core && /tmp/apache-maven-3.9.9/bin/mvn -B -q -Dstyle.color=never -Dmaven.repo.local=.m2-cache/repository -Dspring.profiles.active=test -Dtest=OAuthCredentialServiceTest,OAuthCredentialAdminServiceTest,OAuthCredentialAdminControllerSecurityTest,OAuthCredentialPersistenceTest test` | 4 OAuth suites, 38 tests, 0 failures, 0 errors |
| Frontend targeted | `cd ecm-frontend && CI=true npm test -- --runTestsByPath src/pages/OAuthCredentialAdminPage.test.tsx src/components/layout/MainLayout.menu.test.tsx --watchAll=false` | 2 suites, 17 tests, 0 failures |
| Frontend lint | `cd ecm-frontend && npm run lint` | Pass |
| Frontend production build | `cd ecm-frontend && CI=true npm run build` | Pass; existing bundle-size advisory only |
| Diff hygiene | `git diff --check` | Pass |
| Preflight syntax | `bash -n scripts/oauth-credential-admin-preflight.sh` | Pass |
| OAuth admin preflight | `scripts/oauth-credential-admin-preflight.sh` | Pass: 36 backend targeted tests, 17 frontend targeted tests, lint, production build |
| CI failure follow-up target | `cd ecm-frontend && CI=true npm test -- --runTestsByPath src/pages/RecordsManagementPage.test.tsx --testNamePattern='summarizes selected operations filters and clears them by scope' --watchAll=false --runInBand` | Pass: target test 1/1, 4.8s |
| CI failure follow-up file | `cd ecm-frontend && CI=true npm test -- --runTestsByPath src/pages/RecordsManagementPage.test.tsx --watchAll=false --runInBand` | Pass: 82/82 |

Notes:

- The standalone 38-test backend command includes `OAuthCredentialPersistenceTest`; the preflight script intentionally keeps its existing faster 36-test backend set.
- `OAuthCredentialAdminControllerSecurityTest.revokeReportsProviderFailureAs500` logs the expected synthetic 500 stack trace during the test. The suite still exits successfully.
- `CI=true npm run build` emitted the existing CRA bundle-size advisory and no build failure.
- Remote CI run `25497544404` passed Backend Verify and Phase C, then failed in Frontend Build & Test during full frontend unit tests. The failure was an existing heavy `RecordsManagementPage.test.tsx` interaction timing out after 45s with MUI `TouchRipple` / `TransitionGroup` act warnings. The follow-up fix disables MUI ripple in that test harness through a test-only theme wrapper; it does not change production UI code or OAuth runtime behavior.

## Remaining Work

- Push the CI follow-up fix and wait for the remote CI gate.
- Microsoft provider-side revoke remains a design follow-up because Microsoft does not provide a direct Google-style RFC 7009 revoke endpoint for this per-credential model.
- CUSTOM provider revoke needs a modeled revoke endpoint contract before enabling the action for arbitrary providers.
- Env-managed credential-key-only rows remain intentionally unsupported for provider-side revoke because Athena does not own the external secret material.
