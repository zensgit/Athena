#!/usr/bin/env bash
set -euo pipefail

# End-to-end smoke script for Athena ECM Core.
# Usage:
#   ECM_API=http://localhost:8080 ECM_TOKEN=<token> ./scripts/smoke.sh

BASE_URL="${ECM_API:-http://localhost:8080}"
TOKEN="${ECM_TOKEN:-}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

auth_args=()
if [[ -n "$TOKEN" ]]; then
  auth_args=(-H "Authorization: Bearer $TOKEN")
else
  log_warn "No ECM_TOKEN provided. Some protected endpoints will be skipped or fail."
fi

curl_cmd() {
  curl -fsS "${auth_args[@]}" "$@"
}

extract_id() {
  local json="$1"
  if command -v jq &> /dev/null; then
    echo "$json" | jq -r ".id // empty"
  else
    echo "$json" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4 || true
  fi
}

echo "=== Athena ECM Smoke Check ==="
log_info "Target: ${BASE_URL}"

# 1. Health Check
log_info "Checking Health..."
if curl_cmd "${BASE_URL}/actuator/health" > /dev/null; then
  log_info "Health check passed."
else
  log_error "Health check failed!"
  exit 1
fi

# 2. Pipeline Status
log_info "Checking Pipeline Status..."
# Note: Endpoint might be /api/v1/documents/pipeline/status or just check /actuator/metrics
if curl_cmd "${BASE_URL}/actuator/metrics/process.uptime" > /dev/null; then
    log_info "Metrics endpoint accessible."
else
    log_warn "Metrics endpoint failed (could be auth or not exposed)."
fi

if [[ -z "$TOKEN" ]]; then
  log_warn "Skipping authenticated steps (Upload, Rules, Share, Trash)."
  exit 0
fi

# 3. List Rules
log_info "Listing Rules..."
rules_resp=$(curl_cmd "${BASE_URL}/api/v1/rules?page=0&size=1")
if [[ -n "$rules_resp" ]]; then
  log_info "Rules listed successfully."
else
  log_error "Failed to list rules."
fi

# 4. Upload Document
log_info "Uploading Document..."
tmpfile=$(mktemp /tmp/ecm-smoke-XXXXXX.txt)
echo "Athena smoke test content $(date)" > "$tmpfile"

upload_resp=$(curl_cmd -F "file=@${tmpfile}" "${BASE_URL}/api/v1/documents/upload")
rm -f "$tmpfile"

doc_id=$(extract_id "$upload_resp")

if [[ -n "$doc_id" ]]; then
  log_info "Document uploaded. ID: $doc_id"
else
  log_error "Upload failed or ID not returned."
  echo "Response: $upload_resp"
  exit 1
fi

# 5. Search
log_info "Searching for document..."
sleep 2 # Wait for indexing
search_resp=$(curl_cmd "${BASE_URL}/api/v1/search?q=Athena&size=1")
# Just check if we got a 200 OK mostly
log_info "Search executed."

# 6. Create Share Link
log_info "Creating Share Link..."
share_resp=$(curl_cmd -X POST -H "Content-Type: application/json" \
  -d '{"name":"Smoke Share","permissionLevel":"READ"}' \
  "${BASE_URL}/api/share/nodes/${doc_id}")

share_token=$(echo "$share_resp" | grep -o '"token":"[^"]*"' | head -1 | cut -d'"' -f4 || true)
if [[ -n "$share_token" ]]; then
  log_info "Share link created. Token: $share_token"
else
  log_warn "Failed to create share link (Check JSON parsing or endpoint)."
fi

# 7. Move to Trash
log_info "Moving to Trash..."
if curl_cmd -X POST "${BASE_URL}/api/trash/nodes/${doc_id}" > /dev/null; then
  log_info "Document moved to trash."
else
  log_error "Failed to move to trash."
fi

# 8. List Trash
log_info "Listing Trash..."
trash_resp=$(curl_cmd "${BASE_URL}/api/trash")
if echo "$trash_resp" | grep -q "$doc_id"; then
  log_info "Document found in trash."
else
  log_warn "Document not found in trash list (could be eventual consistency or parsing issue)."
fi

# 9. Restore from Trash
log_info "Restoring from Trash..."
if curl_cmd -X POST "${BASE_URL}/api/trash/${doc_id}/restore" > /dev/null; then
  log_info "Document restored."
else
  log_error "Failed to restore document."
fi

log_info "=== Smoke check finished successfully ==="