# OAuth Credential Admin Preflight Shape-Guard Coverage

Date: 2026-05-12

## Context

The OAuth Credential Admin frontend service now has explicit response-shape guards for inventory and mutation endpoints. Those guards prevent mocked or static browser runs from accepting an SPA HTML fallback as a valid OAuth credential DTO.

The local preflight script still ran the admin page and menu tests, but not `oauthCredentialAdminService.test.ts`. That left a local-gate gap: a page-focused test set could pass while the service-level HTML-fallback guard drifted or was accidentally removed.

## Design

`scripts/oauth-credential-admin-preflight.sh` now includes:

```text
src/services/oauthCredentialAdminService.test.ts
```

in `FRONTEND_TEST_PATHS`, before the page and menu tests.

No backend behavior, frontend runtime behavior, route, or API contract changed. This is a gate-hardening slice only: the local OAuth admin preflight now matches the cross-boundary failure mode that previously required post-integration fixes.

## Verification

| Gate | Command | Result |
|---|---|---|
| Script syntax | `bash -n scripts/oauth-credential-admin-preflight.sh` | passed |
| OAuth admin preflight | `MAVEN_BIN=/tmp/codex-maven/apache-maven-3.9.11/bin/mvn bash scripts/oauth-credential-admin-preflight.sh` | passed: backend 47/47, frontend 35/35, lint clean, production build compiled successfully with the existing CRA bundle-size advisory |
| Whitespace | `git diff --check` | clean |

## Files Changed

- `scripts/oauth-credential-admin-preflight.sh`
- `docs/OAUTH_CREDENTIAL_STORE_CLOSEOUT_TODO_20260507.md`
- `docs/OAUTH_CREDENTIAL_ADMIN_PREFLIGHT_SHAPE_GUARD_COVERAGE_20260512.md`

## Remaining Work

- CI remains the authoritative merge bar after local preflight passes.
- This slice does not add provider capability or change OAuth credential semantics.
