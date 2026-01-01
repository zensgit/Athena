# Frontend API Proxy Stability Report

## Summary
- Addressed stale DNS resolution in the frontend Nginx proxy that caused `/api/v1/*` requests on port 5500 to hit the wrong upstream after container restarts.
- Added dynamic DNS resolution to the Nginx config and reloaded the running container.

## Changes
- Added Nginx DNS resolver and upstream variable to force runtime DNS lookups.

## Verification
- Obtained Keycloak token (admin/admin) and validated `/api/v1/folders/roots` through the frontend container.
- Confirmed the response returns HTTP 200 and expected JSON payload via `http://localhost:5500`.

### Commands
```bash
TOKEN=$(python3 - <<'PY'
import json,urllib.request,urllib.parse

data=urllib.parse.urlencode({"grant_type":"password","client_id":"unified-portal","username":"admin","password":"admin"}).encode()
req=urllib.request.Request("http://localhost:8180/realms/ecm/protocol/openid-connect/token",data=data)
with urllib.request.urlopen(req) as r:
    payload=json.load(r)
print(payload["access_token"])
PY
)

curl -s -D - http://localhost:5500/api/v1/folders/roots \
  -H "Authorization: Bearer $TOKEN"
```

### Result
- HTTP 200
- JSON payload returned with `uploads` and `Root` entries

## Notes
- This change prevents the frontend container from caching a stale IP when `ecm-core` restarts.
- For a full image update, rebuild the frontend image so the new Nginx config is baked in.
