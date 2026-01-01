# Frontend Image Rebuild Report

## Goal
Bake the updated Nginx proxy config into the frontend image and restart the service.

## Changes
- Added `ecm-frontend/Dockerfile.prebuilt` for a lightweight build using prebuilt static assets.
- Updated `docker-compose.override.yml` to use the prebuilt Dockerfile for `ecm-frontend`.
- Allowed `build/` in the frontend build context via `.dockerignore` exception.

## Build & Restart
```bash
docker compose build ecm-frontend
docker compose up -d --no-deps ecm-frontend
```

## Verification
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
- HTTP 200 via `http://localhost:5500/api/v1/folders/roots`.
- JSON payload returned with `uploads` and `Root` entries.

## Notes
- The standard multi-stage build stalled for >7 minutes at `npm run build`; the prebuilt Dockerfile avoids that bottleneck by using the local `build/` output.
- Ensure `npm run build` is executed locally before rebuilding the frontend image.
