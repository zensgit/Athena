# Staging Internal Testing Guide — 2026-05-30

This guide is for internal testing on the current bare-IP staging host.

## Entry Point

- URL: `https://23.254.236.11/`
- Expected browser warning: the certificate is self-signed (`CN=athena.local`).
- Internal testers may proceed through the browser warning only for this staging environment.

Do not present this URL as pilot/customer-facing evidence. Browser-trusted access still needs an
owner-provided hostname or trusted TLS front door, tracked in issue #20.

## What Works

- Public `GET /health` returns `200`.
- Frontend source maps are absent in the deployed image.
- Keycloak is available through the same origin at `/realms/...`.
- OIDC issuer is `https://23.254.236.11:443/realms/ecm`.
- Backend JWT issuer is aligned to that exact issuer.
- Authenticated API smoke passed: a temporary Keycloak user obtained a token through
  `/realms/...`, and authenticated `GET /api/v1/folders/roots` returned `200`.

## Test Account Handling

Use a staging-only Keycloak account created by the owner/operator. Do not reuse production
credentials. Do not paste passwords or access tokens into GitHub issues, docs, or chat.

If a temporary account is created for a smoke test, delete it after the test and record only
the result, not the credential value.

## Known Staging-Only Constraints

- TLS is self-signed on the bare IP. This is acceptable only for internal testing.
- Antivirus is disabled by design on this staging instance; production keeps antivirus enabled.
- The app is running published GHCR images, but this is not a hardened production cutover.
- Pilot/production still requires the owner cutover path: secret rotation, trusted TLS,
  backup/restore smoke, and hardened full-stack smoke.

## Quick Checks

From a shell that can reach the staging host:

```bash
scripts/staging-public-smoke.sh
```

Or run the equivalent manual checks:

```bash
curl -k -I https://23.254.236.11/health
curl -k -sS https://23.254.236.11/realms/ecm/.well-known/openid-configuration
```

Expected:

- `/health` is `200`.
- OIDC discovery has issuer `https://23.254.236.11:443/realms/ecm`.

## Related Records

- `docs/STAGING_ACCEPTANCE_RECEIPT_20260530.md`
- `docs/RUNBOOK_ISSUE20_TLS_AND_THROUGHPUT_20260530.md`
- GitHub issue #20: owner action for trusted TLS/hostname.
- GitHub issue #21: closed internal staging acceptance receipt.
