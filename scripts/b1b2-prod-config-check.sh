#!/usr/bin/env bash
# B1/B2 (matrix §8.3) — static + daemon-free guard for the templated prod config.
#
# Proves the SHAPE of the prod Keycloak/TLS config; it does NOT prove runtime (real TLS handshake,
# real cert, Keycloak booting with a real hostname, token iss match, B4 smoke) — those are owner/ops.
# No Docker daemon required: static text checks + `docker compose config` (merge/validate only).
#
# Usage: scripts/b1b2-prod-config-check.sh   (from repo root; exits non-zero on failure)
set -euo pipefail

PROD=docker-compose.prod.yml
DEV=nginx/nginx.conf
PCONF=nginx/nginx.prod.conf
SNIP=nginx/athena-locations.conf
fail() { echo "FAIL: $1" >&2; exit 1; }

for f in "$PROD" "$DEV" "$PCONF" "$SNIP"; do [ -f "$f" ] || fail "missing file: $f"; done

# --- 1. Keycloak prod posture (docker-compose.prod.yml) -----------------------------------------
# Extract the keycloak block and strip comments (so explanatory text mentioning start-dev/import
# does not trip the negative checks below).
kc=$(awk '/^  keycloak:/{f=1;next} /^  [a-z]/{f=0} f' "$PROD" | sed -E 's/#.*$//')
grep -Eq 'command:\s*\["start"\]' "$PROD"            || fail "keycloak command must be [\"start\"]"
echo "$kc" | grep -q 'start-dev'                      && fail "keycloak must NOT use start-dev in prod"
echo "$kc" | grep -q 'import-realm'                   && fail "keycloak must NOT use --import-realm (out-of-band realm)"
echo "$kc" | grep -Eq 'volumes:\s*!reset \[\]'        || fail "keycloak must drop the realm-import mount via 'volumes: !reset []'"
echo "$kc" | grep -q 'KC_HTTP_ENABLED=true'           || fail "keycloak missing KC_HTTP_ENABLED=true (KC23 prod HTTP listener)"
echo "$kc" | grep -q 'KC_HOSTNAME=${ECM_KEYCLOAK_PUBLIC_HOST:?required}' || fail "keycloak missing fail-fast KC_HOSTNAME"
echo "$kc" | grep -q 'KC_HOSTNAME_PORT=${ECM_KEYCLOAK_PUBLIC_PORT:-443}' || fail "keycloak missing default public hostname port 443"
echo "$kc" | grep -q 'KC_PROXY_HEADERS=xforwarded'    || fail "keycloak missing KC_PROXY_HEADERS=xforwarded"

# --- 2. nginx override mounts the prod conf + snippet -------------------------------------------
grep -q './nginx/nginx.prod.conf:/etc/nginx/nginx.conf' "$PROD"          || fail "nginx override must mount nginx.prod.conf as /etc/nginx/nginx.conf"
grep -q './nginx/athena-locations.conf:/etc/nginx/athena-locations.conf' "$PROD" || fail "nginx override must mount the locations snippet"

# --- 3. Port-80 server is redirect-only (no business locations) --------------------------------
s80=$(awk '/listen 80;/{f=1} f{print} /listen 443/{exit}' "$PCONF")
echo "$s80" | grep -q 'return 301 https' || fail "port-80 server must 301-redirect to https"
echo "$s80" | grep -q 'proxy_pass'       && fail "port-80 server must have NO business locations (found proxy_pass)"
echo "$s80" | grep -q 'athena-locations' && fail "port-80 server must NOT include the business snippet"

# --- 4. Port-443 server: ssl + include + hardened headers --------------------------------------
grep -q 'listen 443 ssl' "$PCONF"                                  || fail "missing 'listen 443 ssl'"
grep -q 'upstream keycloak_backend' "$PCONF"                       || fail "prod conf missing keycloak_backend upstream"
grep -q 'include /etc/nginx/athena-locations.conf;' "$PCONF"       || fail "443 server must include the locations snippet"
grep -q 'Strict-Transport-Security' "$PCONF"                       || fail "HSTS missing in prod conf"
grep -q 'X-Frame-Options' "$PCONF"                                 || fail "X-Frame-Options missing in prod conf"
grep -q 'X-Content-Type-Options' "$PCONF"                          || fail "X-Content-Type-Options missing in prod conf"
grep -q 'Referrer-Policy' "$PCONF"                                 || fail "Referrer-Policy missing in prod conf"
csp=$(grep 'Content-Security-Policy' "$PCONF" || true)
[ -n "$csp" ]                                                      || fail "CSP missing in prod conf"
echo "$csp" | grep -Eq '[^s]http:' && fail "CSP must not contain bare 'http:' (mixed content) — found: $csp"

# --- 5. Snippet = locations only, NO security headers ------------------------------------------
grep -Eq '(Strict-Transport-Security|Content-Security-Policy|X-Frame-Options|X-XSS-Protection|Referrer-Policy)' "$SNIP" \
    && fail "snippet must NOT carry security headers (those belong to the 443 server)"
grep -q 'location /api/' "$SNIP"                                   || fail "snippet missing /api/ location (drift guard)"
grep -q 'documents/upload' "$SNIP"                                 || fail "snippet missing upload location (drift guard)"
grep -q 'location \^~ /realms/' "$SNIP"                            || fail "snippet missing same-origin Keycloak /realms/ location"
grep -q 'location \^~ /resources/' "$SNIP"                         || fail "snippet missing Keycloak /resources/ location"
grep -q 'proxy_pass http://keycloak_backend' "$SNIP"                || fail "snippet missing keycloak_backend proxy"
grep -q 'location / {' "$SNIP"                                     || fail "snippet missing frontend / location (drift guard)"
grep -q 'location /health' "$SNIP"                                 || fail "snippet missing /health (drift guard)"
grep -q 'location /actuator/' "$SNIP"                              || fail "snippet missing /actuator/ (drift guard)"

# --- 6. No real domain / no committed certs ----------------------------------------------------
grep -E '^\s*server_name' "$PCONF" | grep -vq 'server_name _;' && fail "prod conf must use placeholder 'server_name _;' (no real domain committed)"
ls nginx/ssl/*.pem >/dev/null 2>&1 && fail "no cert/key (*.pem) may be committed under nginx/ssl"

# --- 7. Dev nginx.conf untouched (still HTTP-only) ---------------------------------------------
grep -Eq '^\s*listen 443 ssl' "$DEV" && fail "dev nginx.conf must stay HTTP-only (443 block must remain commented)"

# --- 8. Merged config parses on a CLEAN CLONE (daemon-free) ------------------------------------
# `--env-file /dev/null` stops Compose from auto-reading a local ./.env, so this reproduces a fresh
# checkout (no dev env files). Dummy values cover every no-default ${VAR} in base+prod so a clean
# clone validates purely from these exports — not from any residual local .env.
export POSTGRES_DB=d POSTGRES_USER=u POSTGRES_PASSWORD=p ELASTIC_PASSWORD=e REDIS_PASSWORD=r \
  RABBITMQ_USER=ru RABBITMQ_PASSWORD=rp ECM_JWT_ISSUER_URI=https://kc.example/realms/ecm \
  ECM_JWT_JWK_SET_URI=http://keycloak:8080/realms/ecm/protocol/openid-connect/certs \
  ECM_SECURITY_CORS_ALLOWED_ORIGINS=https://app.example MINIO_ROOT_USER=mu MINIO_ROOT_PASSWORD=mp \
  GF_SECURITY_ADMIN_USER=gu GF_SECURITY_ADMIN_PASSWORD=gp ECM_KEYCLOAK_PUBLIC_HOST=kc.example \
  KEYCLOAK_USER=ku KEYCLOAK_PASSWORD=kp KEYCLOAK_DB_USER=kdu KEYCLOAK_DB_PASSWORD=kdp \
  MINIO_ACCESS_KEY=mak MINIO_SECRET_KEY=msk ODOO_DB_USER=odu ODOO_DB_PASSWORD=odp
if command -v docker >/dev/null 2>&1; then
    merged=$(docker compose --env-file /dev/null -f docker-compose.yml -f "$PROD" config 2>/dev/null) \
        || fail "merged 'docker compose config' did not validate on a clean clone (no local .env)"
    # ecm-core must NOT inherit any env_file in prod (dropped via env_file: !reset []).
    printf '%s' "$merged" | python3 -c '
import sys,yaml
d=yaml.safe_load(sys.stdin)
ec=d["services"]["ecm-core"]
ef=ec.get("env_file")
sys.exit(1 if ef else 0)
' || fail "prod ecm-core must drop base env files (env_file: !reset [] missing or ineffective)"
    echo "OK: merged config validates on a clean clone (--env-file /dev/null); ecm-core has no env_file."
else
    echo "SKIP: docker CLI not present — static checks only."
fi

echo "OK: B1/B2 prod config shape verified (static). Runtime cutover (certs/domain/realm/B4) = owner/ops."
