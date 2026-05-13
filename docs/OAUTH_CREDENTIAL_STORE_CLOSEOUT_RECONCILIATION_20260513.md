# OAuth Credential Store Closeout Reconciliation

Date: 2026-05-13

## Context

OAuth Credential Store v1 had continued past the original Google-only revoke
scope. By 2026-05-12 the implementation already included CUSTOM revoke
backend support, CUSTOM endpoint administration, endpoint detail readback,
capability filters, URL state, active filter chips, env-managed-only triage,
and preflight shape-guard coverage.

The remaining risk was documentation drift: the closeout TODO and the original
Microsoft/CUSTOM follow-up still read as if CUSTOM might be future work. That
could cause the next executor to reopen a shipped path instead of preserving
the real boundary: Microsoft provider-side revoke remains unsupported because
there is no honest per-token revoke endpoint for Athena's current credential
model.

## Design

This docs-only reconciliation makes the closeout state explicit:

- CUSTOM revoke is shipped, not pending.
- Microsoft provider-side revoke is intentionally unsupported and must remain
  controlled by backend capability metadata.
- Env-managed credential-key-only rows remain non-revokable inside Athena
  because Athena does not own the external secret material.
- Future OAuth admin work should start only when a new
  `OAuthCredentialOwnerAdapter` appears, Microsoft publishes a compatible
  per-token revoke endpoint, or operators need deeper secret-manager
  integration.

No API, controller, service, frontend route, migration, test, or preflight
script changed in this slice.

## Files Changed

- `docs/OAUTH_CREDENTIAL_STORE_CLOSEOUT_TODO_20260507.md`
- `docs/OAUTH_CREDENTIAL_PROVIDER_REVOKE_MICROSOFT_CUSTOM_DESIGN_FOLLOWUP_20260507.md`
- `docs/OAUTH_CREDENTIAL_STORE_CLOSEOUT_RECONCILIATION_20260513.md`

## Verification

| Gate | Command | Result |
|---|---|---|
| Script syntax | `bash -n scripts/oauth-credential-admin-preflight.sh` | passed |
| OAuth admin preflight | `MAVEN_BIN=/tmp/codex-maven/apache-maven-3.9.11/bin/mvn bash scripts/oauth-credential-admin-preflight.sh` | passed: backend 53/53, frontend 47/47, lint clean, production build compiled successfully |

Observed non-blocking output:

- Backend test logs include the expected mocked 500 stack trace from
  `revokeReportsProviderFailureAs500`; the suite still reports 13/13 controller
  security tests passed and 53/53 backend tests passed overall.
- Frontend tests print existing React Router v7 future-flag warnings from the
  `MemoryRouter` harness.
- Production build prints the existing CRA bundle-size advisory.

## Remaining Work

None for the current OAuth Credential Store v1 closeout surface.

Do not implement a Microsoft "logout" or best-effort button unless the product
explicitly accepts that it does not revoke server-side refresh tokens. The
current safer behavior is to keep Microsoft unsupported and visible through
`providerRevokeSupported=false` plus the backend-owned unsupported reason.
