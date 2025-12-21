#!/usr/bin/env bash
set -euo pipefail

ECM_API_URL="${ECM_API_URL:-http://localhost:7700}"
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8180}"
REALM="${ECM_KEYCLOAK_REALM:-ecm}"
CLIENT_ID="${ECM_KEYCLOAK_CLIENT_ID:-unified-portal}"
CLIENT_SECRET="${ECM_KEYCLOAK_CLIENT_SECRET:-}"
USERNAME="${ECM_KEYCLOAK_USERNAME:-admin}"
PASSWORD="${ECM_KEYCLOAK_PASSWORD:-admin}"
CAD_FILE="${CAD_FILE:-}"
REPORT_DIR="${REPORT_DIR:-/Users/huazhou/Downloads/Github/Athena/docs}"
STAMP=$(date +"%Y%m%d_%H%M%S")
REPORT_PATH="$REPORT_DIR/SMOKE_CAD_PREVIEW_${STAMP}.md"
RENDER_LOG_PATH="${RENDER_LOG_PATH:-/tmp/cad_render_server.log}"
RENDER_LOG_TAIL="${RENDER_LOG_TAIL:-40}"

if [[ -z "$CAD_FILE" ]]; then
  echo "CAD_FILE is required. Example: CAD_FILE=/path/to/file.dwg $0" >&2
  exit 1
fi

if [[ ! -f "$CAD_FILE" ]]; then
  echo "CAD file not found: $CAD_FILE" >&2
  exit 1
fi

mkdir -p "$REPORT_DIR"

TOKEN_ARGS=(
  -d "client_id=$CLIENT_ID"
  -d "grant_type=password"
  -d "username=$USERNAME"
  -d "password=$PASSWORD"
)

if [[ -n "$CLIENT_SECRET" ]]; then
  TOKEN_ARGS+=(-d "client_secret=$CLIENT_SECRET")
fi

TOKEN_JSON=$(curl -s -X POST "$KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/token" "${TOKEN_ARGS[@]}")
TOKEN=$(echo "$TOKEN_JSON" | python3 -c 'import sys,json; payload=json.load(sys.stdin); print(payload.get("access_token",""))')
if [[ -z "$TOKEN" ]]; then
  echo "Failed to fetch token. Response:" >&2
  echo "$TOKEN_JSON" >&2
  exit 1
fi

DOC_JSON=$(curl -s -X POST "$ECM_API_URL/api/v1/documents/upload-legacy" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@$CAD_FILE")

DOC_ID=$(echo "$DOC_JSON" | python3 -c 'import sys,json; payload=json.load(sys.stdin); print(payload.get("documentId") or payload.get("id") or payload.get("uuid") or "")')
if [[ -z "$DOC_ID" ]]; then
  echo "UPLOAD_FAILED" >&2
  echo "$DOC_JSON" >&2
  exit 1
fi

PREVIEW_PATH="/tmp/athena_cad_preview.json"
THUMB_PATH="/tmp/athena_cad_thumb.png"

PREVIEW_STATUS=$(curl -s -o "$PREVIEW_PATH" -w "%{http_code}" -H "Authorization: Bearer $TOKEN" \
  "$ECM_API_URL/api/v1/documents/$DOC_ID/preview")
if [[ "$PREVIEW_STATUS" != "200" ]]; then
  echo "Preview failed (status $PREVIEW_STATUS)" >&2
  exit 1
fi

PREVIEW_SUMMARY=$(python3 - <<'PY'
import json
from pathlib import Path
raw = Path("/tmp/athena_cad_preview.json").read_text(encoding="utf-8")
parsed = json.loads(raw)
print({"supported": parsed.get("supported"), "pageCount": parsed.get("pageCount")})
PY
)
PREVIEW_HAS_NEWLINE=$(python3 - <<'PY'
from pathlib import Path
raw = Path("/tmp/athena_cad_preview.json").read_bytes()
content_idx = raw.find(b'"content"')
if content_idx == -1:
    print("unknown")
else:
    segment = raw[content_idx:]
    print("yes" if b"\\n" in segment else "no")
PY
)

THUMB_STATUS=$(curl -s -o "$THUMB_PATH" -w "%{http_code}" -H "Authorization: Bearer $TOKEN" \
  "$ECM_API_URL/api/v1/documents/$DOC_ID/thumbnail")
if [[ "$THUMB_STATUS" != "200" ]]; then
  echo "Thumbnail failed (status $THUMB_STATUS)" >&2
  exit 1
fi

THUMB_FILE=$(file "$THUMB_PATH")
if [[ "$THUMB_FILE" != *"PNG image data"* ]]; then
  echo "Thumbnail is not PNG" >&2
  echo "$THUMB_FILE" >&2
  exit 1
fi

cat <<EOF > "$REPORT_PATH"
# CAD Preview Smoke Test

- Time: $STAMP
- ECM_API_URL: $ECM_API_URL
- CAD_FILE: $CAD_FILE
- Document ID: $DOC_ID
- Preview: $PREVIEW_SUMMARY
- Preview base64 newline: $PREVIEW_HAS_NEWLINE
- Thumbnail: $THUMB_FILE
- Result: OK

## Render Service Log (tail)
EOF

if [[ -f "$RENDER_LOG_PATH" ]]; then
  {
    echo '```'
    tail -n "$RENDER_LOG_TAIL" "$RENDER_LOG_PATH" | tr -d '\000'
    echo '```'
  } >> "$REPORT_PATH"
else
  echo "(render log not found)" >> "$REPORT_PATH"
fi

echo "$PREVIEW_SUMMARY"
echo "$THUMB_FILE"
echo "OK"

echo "Report: $REPORT_PATH"
