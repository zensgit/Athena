# Requirements: Auth + Routing Stability (2025-12-28)

## Scope
Authentication, routing, and base URL stability across UI/API.

## Observed Issues (From Validation)
- Keycloak redirect loop / URL hash change during login.
- `401 Unauthorized` on token requests in UI.
- 502 Bad Gateway when routing through reverse proxy.
- Manifest/favicon 404s when serving UI directly.

## Requirements
1. **Stable login flow**
   - Avoid redirect loops; show a clear error message when token acquisition fails.
   - Provide a retry option without losing UI state.

2. **Explicit base URLs**
   - UI must use a single, configurable API base URL (e.g., `REACT_APP_API_BASE_URL`).
   - Keycloak endpoint must be configurable per environment.

3. **Token reliability**
   - Handle expired/invalid tokens with a refresh or explicit re-login prompt.
   - Prevent silent failures that manifest as blank screens.

4. **Proxy compatibility**
   - Ensure `X-Forwarded-*` and host headers are handled so Keycloak and API URLs are correct.
   - Avoid 502s caused by mismatched upstream ports.

5. **Static assets**
   - `manifest.json` and `favicon.ico` should be served correctly in dev and prod.
   - Avoid console noise from missing assets.

## Acceptance Criteria (Draft)
- Login completes without URL hash bouncing.
- Invalid token → user sees actionable error.
- API base URL and Keycloak URL are environment-configurable.
- UI loads without missing manifest/favicon in dev mode.
