# Login Redirect Stability Verification

Date: 2025-12-26

## Changes Under Test
- Strip Keycloak callback parameters from the URL after initialization to prevent visible hash flicker.

## Steps
1. Rebuilt and restarted frontend container (`docker compose up -d --build ecm-frontend`).
2. Navigated to `http://localhost:5500/login`.
3. Clicked "Sign in with Keycloak".
4. Landed on `http://localhost:5500/browse/root` and observed the URL for 5 seconds (no hash/redirect oscillation).
5. Queried Keycloak client config via admin API for `unified-portal`.
6. Triggered Logout via account menu and verified Keycloak login screen renders.
7. Re-authenticated with admin credentials and observed `/browse/root` for another 5 seconds.

## Result
- The UI stayed on `/browse/root` without redirect looping or hash flicker during the observation window.
- Keycloak client `unified-portal` includes `http://localhost:5500/*` in redirect URIs and `http://localhost:5500` in Web Origins.
- Logout returned to Keycloak login, and a fresh login landed on `/browse/root` without URL bouncing.

## Notes
- The Keycloak session may already be active, so the login form did not appear in this run.
- If redirect bouncing persists on other machines, re-check Keycloak `unified-portal` redirect URIs and Web Origins for the exact port in use.
