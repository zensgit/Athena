# Yuantus Integrations Verification (2025-12-27)

## Scope
- Validate Yuantus -> Athena integration health using dedicated Athena authorization header.

## Setup
- Yuantus base URL: `http://127.0.0.1:7910`
- Athena base URL: `http://host.docker.internal:7700/api/v1`
- Yuantus auth: JWT via `/api/v1/auth/login`
- Athena auth: Keycloak token via `client_id=unified-portal`
- Request headers:
  - `Authorization: Bearer <YUANTUS_TOKEN>`
  - `X-Athena-Authorization: Bearer <ATHENA_TOKEN>`

## Command
```bash
YUANTUS_TOKEN=$(curl -s -X POST http://127.0.0.1:7910/api/v1/auth/login \
  -H 'content-type: application/json' \
  -d '{"tenant_id":"tenant-1","org_id":"org-1","username":"admin","password":"admin"}' \
  | python3 -c 'import sys,json;print(json.load(sys.stdin).get("access_token",""))')

ATHENA_TOKEN=$(curl -s -X POST http://localhost:8180/realms/ecm/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=password&client_id=unified-portal&username=admin&password=admin' \
  | python3 -c 'import sys,json;print(json.load(sys.stdin).get("access_token",""))')

curl -s http://127.0.0.1:7910/api/v1/integrations/health \
  -H 'x-tenant-id: tenant-1' -H 'x-org-id: org-1' \
  -H "Authorization: Bearer ${YUANTUS_TOKEN}" \
  -H "X-Athena-Authorization: Bearer ${ATHENA_TOKEN}"
```

## Result
- Athena: **OK**
- cad_ml: not running (connection failed)
- dedup_vision: not running (connection failed)

Sample response snippet:
```json
{"athena":{"ok":true},"cad_ml":{"ok":false},"dedup_vision":{"ok":false}}
```

## Notes
- Overall `ok=false` is expected until cad_ml and dedup_vision are started.
