# Phase 21 Permission Diagnostics Verification (2026-02-03)

## Endpoint
`GET /api/v1/security/nodes/{nodeId}/permission-diagnostics?permissionType=READ`

## Steps
```bash
TOKEN=$(cat /tmp/ecm-token.internal)
DOC_ID=$(curl -sS -H "Authorization: Bearer $TOKEN" \
  'http://localhost:7700/api/v1/search?q=e2e&page=0&size=1' | jq -r '.content[0].id // empty')

curl -sS -H "Authorization: Bearer $TOKEN" \
  "http://localhost:7700/api/v1/security/nodes/${DOC_ID}/permission-diagnostics?permissionType=READ"
```

## Result (summary)
```json
{
  "nodeId": "5382301a-55e0-4881-a45e-e3eaf377bc2f",
  "username": "admin",
  "permission": "READ",
  "allowed": true,
  "reason": "ADMIN",
  "dynamicAuthority": null,
  "allowedAuthorities": [],
  "deniedAuthorities": []
}
```

## Outcome
- ✅ Diagnostics endpoint returns structured decision reasoning for the current user.
