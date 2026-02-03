# Phase 19 Search Reindex Execution (2026-02-03)

## Goal
Trigger the full-text search index rebuild and confirm the API accepts an admin JWT.

## Environment
- API: `http://localhost:7700`
- Keycloak (internal): `http://keycloak:8080/realms/ecm`
- Container: `athena-ecm-core-1`

## Steps Performed
1. Generated an admin access token from Keycloak using the internal issuer (`http://keycloak:8080/realms/ecm`).
   - Token stored at `/tmp/ecm-token.internal` on the host.
2. Verified the token could authenticate against `/api/v1/search/diagnostics`.
3. Triggered the rebuild endpoint: `POST /api/v1/search/index/rebuild`.

## Commands (sanitized)
```bash
# 1) Obtain admin token from Keycloak inside the ecm-core container.
# (credentials redacted)
docker exec -i athena-ecm-core-1 sh -lc \
  "curl -fsS -X POST http://keycloak:8080/realms/ecm/protocol/openid-connect/token \
   -H 'Content-Type: application/x-www-form-urlencoded' \
   -d 'grant_type=password&client_id=unified-portal&username=<admin>&password=<redacted>'" \
  | jq -r .access_token > /tmp/ecm-token.internal

# 2) Confirm token works against search diagnostics.
TOKEN=$(cat /tmp/ecm-token.internal)
curl -sS -H "Authorization: Bearer $TOKEN" \
  http://localhost:7700/api/v1/search/diagnostics

# 3) Trigger rebuild.
TOKEN=$(cat /tmp/ecm-token.internal)
curl -sS -X POST -H "Authorization: Bearer $TOKEN" \
  http://localhost:7700/api/v1/search/index/rebuild
```

## Notes
- Earlier 401s were caused by stale/invalid tokens. Using a fresh admin JWT from the internal Keycloak issuer resolved authentication.
- Rebuild was able to complete synchronously on this run.
