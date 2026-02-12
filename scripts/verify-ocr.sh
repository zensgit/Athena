#!/usr/bin/env bash
set -euo pipefail

# End-to-end OCR verification (API-level).
#
# What this verifies:
# - OCR queue endpoint exists and can enqueue work
# - Core calls ml-service OCR endpoint
# - Core persists OCR status/metadata + updates search index
# - Full-text search can find OCR text
#
# Usage:
#   ECM_API=http://localhost:7700 ECM_TOKEN_FILE=tmp/admin.access_token ./scripts/verify-ocr.sh
#   ECM_API=http://localhost:7700 ECM_TOKEN=<token> ./scripts/verify-ocr.sh
#
# Notes:
# - Requires OCR enabled in ecm-core (default is disabled):
#     add `ECM_OCR_ENABLED=true` to .env (gitignored) and restart services.
# - Requires ml-service image built with OCR deps (Tesseract + Poppler).

BASE_URL="${ECM_API:-http://localhost:7700}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

TOKEN="${ECM_TOKEN:-}"
TOKEN_FILE="${ECM_TOKEN_FILE:-}"
if [[ -z "$TOKEN" ]]; then
  if [[ -n "$TOKEN_FILE" && -f "$TOKEN_FILE" ]]; then
    TOKEN="$(cat "$TOKEN_FILE")"
  elif [[ -f "${REPO_ROOT}/tmp/admin.access_token" ]]; then
    TOKEN="$(cat "${REPO_ROOT}/tmp/admin.access_token")"
  fi
fi

if [[ -z "${TOKEN:-}" ]]; then
  echo "ERROR: Missing token. Set ECM_TOKEN or ECM_TOKEN_FILE (or tmp/admin.access_token)." >&2
  exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "ERROR: jq is required for verify-ocr.sh" >&2
  exit 1
fi

auth_args=(-H "Authorization: Bearer $TOKEN")

curl_cmd() {
  curl -fsS "${auth_args[@]}" "$@"
}

token="$(date +%s)"
token="athena-ocr-${token}-$RANDOM"

tmpbase="$(mktemp "${TMPDIR:-/tmp}/athena-ocr.XXXXXX")"
pdffile="${tmpbase}.pdf"
mv "$tmpbase" "$pdffile"

# Minimal single-page PDF with large text (OCR-friendly).
cat > "$pdffile" <<EOF
%PDF-1.4
1 0 obj
<< /Type /Catalog /Pages 2 0 R >>
endobj
2 0 obj
<< /Type /Pages /Kids [3 0 R] /Count 1 >>
endobj
3 0 obj
<< /Type /Page /Parent 2 0 R /MediaBox [0 0 600 200] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>
endobj
4 0 obj
<< /Length 120 >>
stream
BT
/F1 36 Tf
50 120 Td
(${token}) Tj
0 -50 Td
(ATHENA OCR VERIFY) Tj
ET
endstream
endobj
5 0 obj
<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>
endobj
xref
0 6
0000000000 65535 f 
0000000009 00000 n 
0000000058 00000 n 
0000000115 00000 n 
0000000260 00000 n 
0000000432 00000 n 
trailer
<< /Root 1 0 R /Size 6 >>
startxref
502
%%EOF
EOF

cleanup() {
  rm -f "$pdffile"
}
trap cleanup EXIT

# Resolve an upload folder (prefer uploads root).
roots="$(curl_cmd "${BASE_URL}/api/v1/folders/roots")"
root_id="$(echo "$roots" | jq -r '(.[] | select(.name=="uploads") | .id) // (.[0].id // empty)')"
if [[ -z "${root_id:-}" ]]; then
  echo "ERROR: failed to resolve root folder id" >&2
  exit 1
fi

# Create a dedicated folder for this run (best-effort).
folder_name="ocr-verify-$(date +%s)"
folder_resp="$(curl -sS "${auth_args[@]}" -X POST -H "Content-Type: application/json" \
  -d "{\"name\":\"${folder_name}\",\"parentId\":\"${root_id}\",\"folderType\":\"GENERAL\",\"inheritPermissions\":true}" \
  "${BASE_URL}/api/v1/folders" || true)"
folder_id="$(echo "$folder_resp" | jq -r '.id // empty' 2>/dev/null || true)"
upload_parent_id="${folder_id:-$root_id}"

echo "=== OCR Verify ==="
echo "api=${BASE_URL}"
echo "token=${token}"

# Upload test PDF.
upload_resp="$(curl_cmd -F "file=@${pdffile};type=application/pdf;filename=${token}.pdf" \
  "${BASE_URL}/api/v1/documents/upload?folderId=${upload_parent_id}")"
doc_id="$(echo "$upload_resp" | jq -r '.documentId // .id // empty')"
if [[ -z "${doc_id:-}" ]]; then
  echo "ERROR: upload did not return a document id" >&2
  echo "$upload_resp" | head -c 800 && echo
  exit 1
fi
echo "doc_id=${doc_id}"

# Enqueue OCR (force=true).
queue_resp="$(curl -sS "${auth_args[@]}" -X POST "${BASE_URL}/api/v1/documents/${doc_id}/ocr/queue?force=true" || true)"
queued="$(echo "$queue_resp" | jq -r '.queued // false' 2>/dev/null || echo false)"
message="$(echo "$queue_resp" | jq -r '.message // empty' 2>/dev/null || true)"
if [[ "${queued}" != "true" ]]; then
  echo "ERROR: OCR was not queued. response=${queue_resp}" >&2
  if [[ -n "${message:-}" ]]; then
    echo "message=${message}" >&2
  fi
  echo "Hint: set ECM_OCR_ENABLED=true in .env and restart services." >&2
  exit 2
fi

echo "queued=true"

# Poll OCR status on the node metadata.
deadline="$(( $(date +%s) + ${ECM_OCR_WAIT_SECONDS:-180} ))"
status=""
reason=""
while [[ $(date +%s) -lt "$deadline" ]]; do
  node="$(curl_cmd "${BASE_URL}/api/v1/nodes/${doc_id}")"
  status="$(echo "$node" | jq -r '.metadata.ocrStatus // empty')"
  reason="$(echo "$node" | jq -r '.metadata.ocrFailureReason // empty')"
  if [[ "$status" == "READY" ]]; then
    break
  fi
  if [[ "$status" == "FAILED" ]]; then
    echo "ERROR: OCR failed reason=${reason:-unknown}" >&2
    exit 1
  fi
  sleep 3
done

if [[ "$status" != "READY" ]]; then
  echo "ERROR: OCR did not reach READY within timeout (status=${status:-<none>})" >&2
  if [[ -n "${reason:-}" ]]; then
    echo "reason=${reason}" >&2
  fi
  exit 1
fi

echo "ocr_status=READY"

# Poll search index for the unique token.
search_deadline="$(( $(date +%s) + ${ECM_OCR_SEARCH_WAIT_SECONDS:-90} ))"
found="0"
while [[ $(date +%s) -lt "$search_deadline" ]]; do
  resp="$(curl_cmd --get --data-urlencode "q=${token}" --data-urlencode "page=0" --data-urlencode "size=10" \
    "${BASE_URL}/api/v1/search")"
  if echo "$resp" | jq -e --arg id "$doc_id" '.content[]? | select(.id == $id)' >/dev/null 2>&1; then
    found="1"
    break
  fi
  sleep 3
done

if [[ "$found" != "1" ]]; then
  echo "ERROR: Search did not return the OCR token within timeout." >&2
  exit 1
fi

echo "search=OK"
echo "OK"

