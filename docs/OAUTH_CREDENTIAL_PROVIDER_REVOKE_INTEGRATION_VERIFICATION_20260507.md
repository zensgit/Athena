# OAuth Credential Provider Revoke - Integration Verification

Date: 2026-05-07

## Context

This document records the Codex takeover and verification of the three Claude-developed OAuth Credential Store packages:

- Backend Google provider-side revoke: `06be7fb`
- Frontend Provider Revoke action: `9ea6ce7`
- OAuth admin preflight and closeout TODO: `a114f1d`
- Integration note reconciliation: `a7275da`

The packages were integrated in the intended order: backend, frontend, then preflight/docs.

## Review Notes

The implementation keeps the v1 revoke scope bounded:

- Google only.
- Refresh token is preferred; access token is fallback.
- Env-managed credential-key-only rows and unsupported providers return explicit unsupported responses.
- Provider success or already-invalid token clears local tokens through the owner adapter.
- Provider 5xx and network failures preserve local tokens for retry.
- The frontend enables Provider Revoke only for Google rows with stored local tokens.

Codex adjusted the closeout documentation after integration so it no longer described backend/frontend revoke as in-flight and no longer claimed an unsupported-provider error code that the API does not expose.

## Local Verification

Backend targeted tests:

```bash
cd ecm-core
/tmp/apache-maven-3.9.9/bin/mvn -B -q -Dstyle.color=never \
  -Dmaven.repo.local=.m2-cache/repository \
  -Dspring.profiles.active=test \
  -Dtest=OAuthCredentialServiceTest,OAuthCredentialAdminServiceTest,OAuthCredentialAdminControllerSecurityTest test
```

Result: passed. Test total: 28 tests, 0 failures, 0 errors.

Frontend targeted tests:

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath \
  src/pages/OAuthCredentialAdminPage.test.tsx \
  src/components/layout/MainLayout.menu.test.tsx \
  --watchAll=false
```

Result: passed. Test total: 2 suites, 16 tests.

Static gates:

```bash
git diff --check
bash -n scripts/oauth-credential-admin-preflight.sh
cd ecm-frontend && npm run lint
cd ecm-frontend && CI=true npm run build
```

Result: passed. The build emitted only the existing bundle-size advisory and Node `fs.F_OK` deprecation warning.

Integrated preflight:

```bash
scripts/oauth-credential-admin-preflight.sh
```

Result: passed. The script ran backend targeted tests, frontend targeted tests, frontend lint, and production build.

## Remote CI

Run:

```bash
gh run view 25494318423 --repo zensgit/Athena
```

Result: success for commit `a7275da`.

Green jobs:

- Backend Verify
- Frontend Build & Test
- Phase C Security Verification
- Acceptance Smoke (3 admin pages)
- Property Encryption Closeout Gate
- Frontend E2E Core Gate
- Phase 5 Mocked Regression Gate

CI emitted GitHub Actions Node.js 20 deprecation warnings from workflow actions. These are workflow-runtime warnings, not product regressions from this slice.

## Remaining Work

- Microsoft provider-side revoke remains a separate design and implementation slice.
- CUSTOM provider-side revoke needs a deterministic revoke-endpoint contract before implementation.
- Additional OAuth owner adapters remain out of scope; the current in-tree adapter remains `MailOAuthCredentialOwnerAdapter`.
