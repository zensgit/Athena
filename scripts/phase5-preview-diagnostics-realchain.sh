#!/usr/bin/env bash
set -euo pipefail

# Real-chain verification for Admin Preview Diagnostics:
# - Upload a deliberately corrupted PDF
# - Wait for preview pipeline to mark it FAILED/UNSUPPORTED
# - Assert it appears in /api/v1/preview/diagnostics/failures
#
# Usage:
#   bash scripts/phase5-preview-diagnostics-realchain.sh [username] [password]
#
# Env overrides:
#   ECM_API_URL (default http://localhost:7700)
#   LIMIT (default 200)
#   POLL_SECONDS (default 120)
#   POLL_INTERVAL (default 3)
#   EXPECT_REASON_SUBSTR (optional substring match for previewFailureReason)

USERNAME="${1:-${ECM_E2E_USERNAME:-admin}}"
PASSWORD="${2:-${ECM_E2E_PASSWORD:-admin}}"

ECM_API_URL="${ECM_API_URL:-http://localhost:7700}"
LIMIT="${LIMIT:-200}"
POLL_SECONDS="${POLL_SECONDS:-120}"
POLL_INTERVAL="${POLL_INTERVAL:-3}"
EXPECT_REASON_SUBSTR="${EXPECT_REASON_SUBSTR:-}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "phase5_preview_realchain: start"
echo "ECM_API_URL=${ECM_API_URL} LIMIT=${LIMIT} POLL_SECONDS=${POLL_SECONDS} POLL_INTERVAL=${POLL_INTERVAL}"

pushd "${ROOT_DIR}" >/dev/null
bash "${SCRIPT_DIR}/get-token.sh" "${USERNAME}" "${PASSWORD}" >/dev/null
TOKEN_FILE="tmp/${USERNAME}.access_token"
TOKEN="$(cat "${TOKEN_FILE}" 2>/dev/null || true)"
popd >/dev/null

if [[ -z "${TOKEN}" ]]; then
  echo "ERROR: failed to obtain access token (expected ${TOKEN_FILE})"
  exit 1
fi

echo "phase5_preview_realchain: create corrupted pdf"
TMP_PDF_BASE="$(mktemp /tmp/ecm-preview-corrupt.XXXXXX)"
TMP_PDF="${TMP_PDF_BASE}.pdf"
mv -f "${TMP_PDF_BASE}" "${TMP_PDF}"
cat >"${TMP_PDF}" <<'EOF'
%PDF-1.4
1 0 obj
<< /Type /Catalog >>
endobj
trailer
<< >>
%%EOF
EOF

STAMP="$(date +%Y%m%d-%H%M%S)"
NAME="e2e-preview-diagnostics-corrupt-${STAMP}.pdf"
FILE="/tmp/${NAME}"
cp -f "${TMP_PDF}" "${FILE}"

echo "phase5_preview_realchain: resolve root folder id"
roots_json="$(curl -fsS -H "Authorization: Bearer ${TOKEN}" "${ECM_API_URL}/api/v1/folders/roots")"
root_id="$(echo "${roots_json}" | jq -r '([.[] | select(((.folderType // "") | ascii_upcase)=="SYSTEM" and ((.path // "")=="/Root") and ((.name // "")=="Root"))][0].id) // (.[0].id // empty)')"
if [[ -z "${root_id}" ]]; then
  echo "ERROR: failed to resolve root folder id"
  echo "${roots_json}" | head -c 400 || true
  echo
  exit 1
fi
echo "root_id=${root_id}"

echo "phase5_preview_realchain: upload corrupted pdf"
upload_http_and_body="$(curl -sS -w '\n__HTTP_STATUS__:%{http_code}' -H "Authorization: Bearer ${TOKEN}" \
  -F "file=@${FILE};type=application/pdf" \
  "${ECM_API_URL}/api/v1/documents/upload?folderId=${root_id}")"

upload_http="$(echo "${upload_http_and_body}" | sed -n 's/^__HTTP_STATUS__://p' | tail -n 1)"
upload_resp="$(echo "${upload_http_and_body}" | sed '/^__HTTP_STATUS__:/d')"

doc_id="$(echo "${upload_resp}" | jq -r '.documentId // empty' 2>/dev/null || true)"
if [[ -z "${doc_id}" ]]; then
  echo "ERROR: upload did not return documentId (http=${upload_http})"
  echo "${upload_resp}" | head -c 600 || true
  echo
  exit 1
fi
if [[ "${upload_http}" != "200" && "${upload_http}" != "201" && "${upload_http}" != "202" && "${upload_http}" != "400" ]]; then
  echo "ERROR: unexpected upload status ${upload_http}"
  echo "${upload_resp}" | head -c 600 || true
  echo
  exit 1
fi
echo "uploaded_document_id=${doc_id} name=${NAME}"

echo "phase5_preview_realchain: poll preview failures endpoint for uploaded doc name"
deadline=$(( $(date +%s) + POLL_SECONDS ))
last_count=0
while [[ "$(date +%s)" -lt "${deadline}" ]]; do
  failures_json="$(curl -fsS -H "Authorization: Bearer ${TOKEN}" "${ECM_API_URL}/api/v1/preview/diagnostics/failures?limit=${LIMIT}" || true)"
  if [[ -n "${failures_json}" ]]; then
    last_count="$(echo "${failures_json}" | jq -r 'length' 2>/dev/null || echo 0)"
    match="$(echo "${failures_json}" | jq -c --arg name "${NAME}" '.[] | select(.name==$name) | {id,name,previewStatus,previewFailureCategory,previewFailureReason,previewLastUpdated}' | head -n 1)"
    if [[ -n "${match}" ]]; then
      echo "FOUND: ${match}"
      if [[ -n "${EXPECT_REASON_SUBSTR}" ]]; then
        reason="$(echo "${match}" | jq -r '.previewFailureReason // ""')"
        if [[ "${reason}" != *"${EXPECT_REASON_SUBSTR}"* ]]; then
          echo "FAIL: previewFailureReason did not include EXPECT_REASON_SUBSTR='${EXPECT_REASON_SUBSTR}'"
          exit 3
        fi
      fi
      echo "phase5_preview_realchain: ok"
      exit 0
    fi
  fi
  sleep "${POLL_INTERVAL}"
done

echo "FAIL: uploaded doc did not appear in preview failures within ${POLL_SECONDS}s (last_failures_count=${last_count})"
echo "hint: ensure preview pipeline workers are running and the file actually produces a FAILED/UNSUPPORTED status"
exit 2
