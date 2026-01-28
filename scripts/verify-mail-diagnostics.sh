#!/usr/bin/env bash
set -euo pipefail

# Verify mail diagnostics API returns recent processed + document entries.
# Usage:
#   ./scripts/verify-mail-diagnostics.sh [username] [password]
#
# Env overrides:
#   API_URL (default http://localhost:7700)
#   LIMIT (default 5)
#   EXPECTED_MIN (default 1) minimum recentDocuments entries

USERNAME="${1:-${ECM_E2E_USERNAME:-admin}}"
PASSWORD="${2:-${ECM_E2E_PASSWORD:-admin}}"
API_URL="${API_URL:-http://localhost:7700}"
LIMIT="${LIMIT:-5}"
EXPECTED_MIN="${EXPECTED_MIN:-1}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

pushd "${ROOT_DIR}" >/dev/null
bash "${SCRIPT_DIR}/get-token.sh" "${USERNAME}" "${PASSWORD}" >/dev/null
TOKEN_FILE="tmp/${USERNAME}.access_token"
TOKEN="$(cat "${TOKEN_FILE}")"
popd >/dev/null

if [[ -z "${TOKEN}" ]]; then
  echo "ERROR: Failed to read access token from ${TOKEN_FILE}"
  exit 1
fi

response="$(curl -fsS "${API_URL}/api/v1/integration/mail/diagnostics?limit=${LIMIT}" \
  -H "Authorization: Bearer ${TOKEN}")"

trimmed="${response//[[:space:]]/}"
if [[ -z "${trimmed}" ]]; then
  echo "ERROR: Empty response from diagnostics endpoint"
  exit 1
fi

echo "${response}" | python3 -c 'import json,os,sys
data = json.load(sys.stdin)
limit = data.get("limit")
recent_processed = len(data.get("recentProcessed") or [])
recent_documents = len(data.get("recentDocuments") or [])
print(f"limit={limit} recentProcessed={recent_processed} recentDocuments={recent_documents}")
expected_min = int(os.environ.get("EXPECTED_MIN", "1"))
if recent_documents < expected_min:
    print(f"FAIL: recentDocuments {recent_documents} < expected {expected_min}")
    sys.exit(2)
print("OK")'
