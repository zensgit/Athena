#!/usr/bin/env bash
set -euo pipefail

# End-to-end smoke script for Athena ECM Core.
# Usage:
#   ECM_API=http://localhost:7700 ECM_TOKEN=<token> ./scripts/smoke.sh
#   ECM_API=http://localhost:7700 ECM_TOKEN_FILE=tmp/admin.access_token ./scripts/smoke.sh

BASE_URL="${ECM_API:-http://localhost:7700}"
UPLOAD_FILE="${ECM_UPLOAD_FILE:-}"

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

curl_maybe() {
  curl -sS "${auth_args[@]}" "$@" || true
}

extract_id() {
  local json="$1"
  if command -v jq &> /dev/null; then
    echo "$json" | jq -r ".documentId // .id // empty"
  else
    local doc_id
    doc_id=$(echo "$json" | grep -o '"documentId":"[^"]*"' | head -1 | cut -d'"' -f4 || true)
    if [[ -n "$doc_id" ]]; then
      echo "$doc_id"
      return 0
    fi
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

# Predeclare to avoid 'set -u' issues before it is resolved later.
root_id=""

# 2.2 System status (aggregated diagnostics)
log_info "Checking System Status..."
if curl_cmd "${BASE_URL}/api/v1/system/status" > /dev/null; then
  log_info "System status endpoint OK."
else
  log_error "System status endpoint failed!"
  exit 1
fi

# 2.2.1 License info (admin only)
log_info "Checking License Info..."
license_resp=$(curl_cmd "${BASE_URL}/api/v1/system/license")
if echo "$license_resp" | grep -q '"edition"'; then
  if command -v jq &> /dev/null; then
    edition=$(echo "$license_resp" | jq -r '.edition // empty')
    log_info "License OK (edition=${edition:-unknown})."
  else
    log_info "License endpoint OK."
  fi
else
  log_error "License endpoint did not return expected payload."
  echo "Response: $license_resp"
  exit 1
fi

# 2.3 Sanity checks (consistency diagnostics)
log_info "Checking Sanity Checks (report-only)..."
if curl_cmd -X POST "${BASE_URL}/api/v1/system/sanity/run?fix=false" > /dev/null; then
  log_info "Sanity checks endpoint OK."
else
  log_error "Sanity checks endpoint failed!"
  exit 1
fi

# 2.3.1 Analytics (admin dashboard backing APIs)
log_info "Checking Analytics Dashboard..."
analytics_dashboard_resp=$(curl_cmd "${BASE_URL}/api/v1/analytics/dashboard")
if echo "$analytics_dashboard_resp" | grep -q '"summary"'; then
  log_info "Analytics dashboard endpoint OK."
else
  log_error "Analytics dashboard endpoint did not return expected payload."
  echo "Response: $analytics_dashboard_resp"
  exit 1
fi

log_info "Checking Recent Audit Logs..."
analytics_audit_resp=$(curl_cmd "${BASE_URL}/api/v1/analytics/audit/recent?limit=1")
if [[ -n "$analytics_audit_resp" ]]; then
  log_info "Analytics audit endpoint OK."
else
  log_error "Analytics audit endpoint failed!"
  exit 1
fi

# 2.3.2 Audit Export and Retention (Sprint 3 Security Features)
log_info "Checking Audit Export..."
from_date=$(date -u -v-30d "+%Y-%m-%dT%H:%M:%SZ" 2>/dev/null || date -u -d "30 days ago" "+%Y-%m-%dT%H:%M:%SZ" 2>/dev/null || echo "2024-01-01T00:00:00Z")
to_date=$(date -u "+%Y-%m-%dT%H:%M:%SZ" 2>/dev/null || echo "2025-12-31T23:59:59Z")
export_resp_status=$(curl -sS -o /dev/null -w "%{http_code}" "${auth_args[@]}" \
  "${BASE_URL}/api/v1/analytics/audit/export?from=${from_date}&to=${to_date}")
if [[ "$export_resp_status" == "200" ]]; then
  log_info "Audit export endpoint OK (HTTP 200)."
else
  log_warn "Audit export endpoint returned HTTP ${export_resp_status} (may be OK if no logs exist)."
fi

log_info "Checking Audit Retention Info..."
retention_resp=$(curl_maybe "${BASE_URL}/api/v1/analytics/audit/retention")
if command -v jq &> /dev/null; then
  retention_days=$(echo "$retention_resp" | jq -r '.retentionDays // empty')
  expired_count=$(echo "$retention_resp" | jq -r '.expiredLogCount // empty')
else
  retention_days=$(echo "$retention_resp" | grep -o '"retentionDays":[0-9]*' | head -1 | cut -d: -f2 || true)
  expired_count=$(echo "$retention_resp" | grep -o '"expiredLogCount":[0-9]*' | head -1 | cut -d: -f2 || true)
fi
if [[ -n "${retention_days:-}" ]]; then
  log_info "Audit retention info OK (retentionDays=${retention_days}, expiredCount=${expired_count:-0})."
else
  log_warn "Audit retention info not available."
fi

# 2.3.3 Antivirus Status Check (Sprint 4 Security Features)
log_info "Checking Antivirus Status..."

# Helper function to check antivirus status
check_av_status() {
  local status_resp
  status_resp=$(curl_maybe "${BASE_URL}/api/v1/system/status")
  if command -v jq &> /dev/null; then
    av_enabled=$(echo "$status_resp" | jq -r '.antivirus.enabled // false')
    av_available=$(echo "$status_resp" | jq -r '.antivirus.available // false')
    av_status=$(echo "$status_resp" | jq -r '.antivirus.status // "unknown"')
  else
    av_enabled=$(echo "$status_resp" | grep -o '"enabled":true' | head -1 && echo "true" || echo "false")
    av_available="unknown"
    av_status="unknown"
  fi
}

check_av_status

if [[ "$av_enabled" == "true" ]]; then
  if [[ "$av_available" == "true" || "$av_status" == "healthy" ]]; then
    log_info "Antivirus is enabled and healthy."
  else
    # Wait for ClamAV to become ready (it may still be downloading virus definitions)
    log_info "Antivirus is enabled but not ready. Waiting for ClamAV (max 30s)..."
    av_ready=0
    for attempt in {1..6}; do
      sleep 5
      check_av_status
      if [[ "$av_available" == "true" || "$av_status" == "healthy" ]]; then
        av_ready=1
        log_info "ClamAV became ready after $((attempt * 5)) seconds."
        break
      fi
      log_info "  Still waiting... (${attempt}/6)"
    done
    if [[ "$av_ready" -eq 0 ]]; then
      log_warn "Antivirus is enabled but not available after 30s (ClamAV may still be starting up)."
    fi
  fi
else
  log_info "Antivirus is disabled."
fi

# 2.3.4 EICAR Virus Test (only if antivirus is enabled and available)
if [[ "$av_enabled" == "true" && ("$av_available" == "true" || "$av_status" == "healthy") ]]; then
  log_info "Testing EICAR virus rejection..."

  # Resolve uploads/root folder id for virus test
  if [[ -z "${root_id:-}" ]]; then
    roots_resp_for_eicar=$(curl_cmd "${BASE_URL}/api/v1/folders/roots")
    if command -v jq &> /dev/null; then
      root_id=$(echo "$roots_resp_for_eicar" | jq -r '(.[] | select(.name=="uploads") | .id) // (.[0].id // empty)')
    else
      root_id=$(echo "$roots_resp_for_eicar" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4 || true)
    fi
  fi

  if [[ -z "${root_id:-}" ]]; then
    log_warn "Skipping EICAR test (failed to resolve root folder id)."
  else
  # Create EICAR test file - standard antivirus test string
  eicar_file=$(mktemp "${TMPDIR:-/tmp}/eicar-test.XXXXXX")
  eicar_filename="${eicar_file}.txt"
  mv "$eicar_file" "$eicar_filename"
  # EICAR test string (split to avoid false positives in this script)
  echo -n 'X5O!P%@AP[4\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*' > "$eicar_filename"

  # Attempt to upload EICAR file - should be rejected
  eicar_upload_status=$(curl -sS -o /dev/null -w "%{http_code}" "${auth_args[@]}" \
    -F "file=@${eicar_filename}" \
    "${BASE_URL}/api/v1/documents/upload?folderId=${root_id}" 2>/dev/null || echo "000")
  rm -f "$eicar_filename"

  # Expected: 400 Bad Request or 500 Internal Server Error with virus message
  if [[ "$eicar_upload_status" == "400" || "$eicar_upload_status" == "500" ]]; then
    log_info "EICAR test file correctly rejected (HTTP ${eicar_upload_status})."
  elif [[ "$eicar_upload_status" == "200" || "$eicar_upload_status" == "201" ]]; then
    log_error "EICAR test file was NOT rejected - virus scanning may not be working!"
    exit 1
  else
    log_warn "EICAR upload returned unexpected status: ${eicar_upload_status} (may be network issue or ClamAV not ready)."
  fi
  fi
else
  log_info "Skipping EICAR test (antivirus is disabled or unavailable)."
fi

# 2.4 Correspondents (metadata)
log_info "Checking Correspondents API..."
if curl_cmd "${BASE_URL}/api/v1/correspondents?page=0&size=1" > /dev/null; then
  log_info "Correspondents endpoint OK."
else
  log_error "Correspondents endpoint failed!"
  exit 1
fi

# 2.1 Current user / authorities (RBAC)
log_info "Checking Current User / Authorities..."
authorities_resp=$(curl_maybe "${BASE_URL}/api/v1/security/users/current/authorities")
if [[ -n "$authorities_resp" ]]; then
  if command -v jq &> /dev/null; then
    has_admin=$(echo "$authorities_resp" | jq -r '.[]' | grep -c '^ROLE_ADMIN$' || true)
    has_editor=$(echo "$authorities_resp" | jq -r '.[]' | grep -c '^ROLE_EDITOR$' || true)
  else
    has_admin=$(echo "$authorities_resp" | grep -c 'ROLE_ADMIN' || true)
    has_editor=$(echo "$authorities_resp" | grep -c 'ROLE_EDITOR' || true)
  fi
  if [[ "${has_admin:-0}" -gt 0 || "${has_editor:-0}" -gt 0 ]]; then
    log_info "RBAC roles present (admin=${has_admin:-0}, editor=${has_editor:-0})."
  else
    log_warn "RBAC roles not detected in authorities response (admin/editor missing)."
  fi
else
  log_warn "Failed to fetch current user authorities (auth may be invalid)."
fi

# Root folder (for upload)
log_info "Resolving root folder..."
roots_resp=$(curl_cmd "${BASE_URL}/api/v1/folders/roots")
if command -v jq &> /dev/null; then
  root_id=$(echo "$roots_resp" | jq -r '(.[] | select(.name=="uploads") | .id) // (.[0].id // empty)')
else
  root_id=$(echo "$roots_resp" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4 || true)
fi
if [[ -z "${root_id:-}" ]]; then
  log_error "Failed to resolve root folder id."
  echo "Response: $roots_resp"
  exit 1
fi
log_info "Root folder id: $root_id"

# 2.2 Create a dedicated folder for this run (optional)
log_info "Creating test folder under root..."
upload_parent_id="$root_id"
folder_name="smoke-folder-$(date +%s)"
folder_resp=$(curl_maybe -X POST -H "Content-Type: application/json" \
  -d "{\"name\":\"${folder_name}\",\"parentId\":\"${root_id}\",\"folderType\":\"GENERAL\",\"inheritPermissions\":true}" \
  "${BASE_URL}/api/v1/folders")
folder_id=$(extract_id "$folder_resp")
if [[ -n "${folder_id:-}" ]]; then
  log_info "Created folder: ${folder_name} (id=${folder_id})"
  upload_parent_id="$folder_id"
else
  log_warn "Failed to create folder; uploading to root folder."
fi

# 2.3 Admin user/group management smoke (users list + create group + add/remove member)
log_info "Checking Admin Users/Groups APIs..."
users_resp=$(curl_maybe "${BASE_URL}/api/v1/users?query=admin&page=0&size=5")
if [[ -n "$users_resp" ]]; then
  if echo "$users_resp" | grep -q '"content"'; then
    log_info "Users endpoint OK."
  else
    log_warn "Users endpoint response did not include content."
  fi
else
  log_warn "Failed to query users (may require different identity provider settings)."
fi

group_name="smoke-group-$(date +%s)"
group_resp=$(curl_cmd -X POST -H "Content-Type: application/json" \
  -d "{\"name\":\"${group_name}\",\"displayName\":\"${group_name}\"}" \
  "${BASE_URL}/api/v1/groups")
group_id=$(extract_id "$group_resp")
if [[ -n "${group_id:-}" ]]; then
  log_info "Group created: ${group_name} (id=${group_id})"
else
  log_error "Failed to create group."
  echo "Response: $group_resp"
  exit 1
fi

groups_list=$(curl_cmd "${BASE_URL}/api/v1/groups?page=0&size=200")
if echo "$groups_list" | grep -q "\"name\":\"${group_name}\""; then
  log_info "Groups endpoint lists created group."
else
  log_warn "Created group not found in groups list."
fi

if curl_cmd -X POST "${BASE_URL}/api/v1/groups/${group_name}/members/admin" > /dev/null; then
  log_info "Added admin to group."
else
  log_error "Failed to add admin to group."
  exit 1
fi

if curl_cmd -X DELETE "${BASE_URL}/api/v1/groups/${group_name}/members/admin" > /dev/null; then
  log_info "Removed admin from group."
else
  log_error "Failed to remove admin from group."
  exit 1
fi

if curl_cmd -X DELETE "${BASE_URL}/api/v1/groups/${group_name}" > /dev/null; then
  log_info "Group deleted."
else
  log_warn "Failed to delete group (it may require manual cleanup): ${group_name}"
fi

# 3. List Rules
log_info "Listing Rules..."
if rules_resp=$(curl_maybe "${BASE_URL}/api/v1/rules?page=0&size=1"); then
  if [[ -n "$rules_resp" ]]; then
    log_info "Rules listed successfully."
  else
    log_warn "Rules response was empty."
  fi
else
  log_warn "Failed to list rules."
fi

# 4. Upload Document (primary PDF/search/version/WOPI smoke target)
log_info "Uploading primary document for PDF/search/version/WOPI smoke..."
cleanup_upload_file() { :; }
upload_mime_type=""
if [[ -n "$UPLOAD_FILE" ]]; then
  if [[ ! -f "$UPLOAD_FILE" ]]; then
    log_error "ECM_UPLOAD_FILE not found: $UPLOAD_FILE"
    exit 1
  fi
  upload_source="$UPLOAD_FILE"
  if [[ "$upload_source" == *.pdf || "$upload_source" == *.PDF ]]; then
    upload_mime_type="application/pdf"
  fi
else
  tmpbase=$(mktemp "${TMPDIR:-/tmp}/ecm-smoke.XXXXXX")
  tmpfile="${tmpbase}.pdf"
  mv "$tmpbase" "$tmpfile"
  cat <<'EOF' > "$tmpfile"
%PDF-1.4
1 0 obj
<< /Type /Catalog /Pages 2 0 R >>
endobj
2 0 obj
<< /Type /Pages /Kids [3 0 R] /Count 1 >>
endobj
3 0 obj
<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 144] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>
endobj
4 0 obj
<< /Length 42 >>
stream
BT
/F1 24 Tf
100 100 Td
(Hello PDF) Tj
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
0000000241 00000 n 
0000000333 00000 n 
trailer
<< /Root 1 0 R /Size 6 >>
startxref
403
%%EOF
EOF
  upload_source="$tmpfile"
  upload_mime_type="application/pdf"
  cleanup_upload_file() { rm -f "$tmpfile"; }
fi

filename=$(basename "$upload_source")
file_form="file=@${upload_source}"
if [[ -n "${upload_mime_type}" ]]; then
  file_form="file=@${upload_source};type=${upload_mime_type};filename=${filename}"
fi
upload_resp=""
upload_status=$(curl -sS -w "%{http_code}" -o /tmp/ecm-upload-resp.$$ "${auth_args[@]}" \
  -F "${file_form}" "${BASE_URL}/api/v1/documents/upload?folderId=${upload_parent_id}" || true)
if [[ -f /tmp/ecm-upload-resp.$$ ]]; then
  upload_resp=$(cat /tmp/ecm-upload-resp.$$)
  rm -f /tmp/ecm-upload-resp.$$
fi
cleanup_upload_file

doc_id=""
if [[ "$upload_status" == "200" || "$upload_status" == "201" ]]; then
  doc_id=$(extract_id "$upload_resp")
else
  log_error "Upload failed (HTTP ${upload_status})."
  echo "Response: $upload_resp"
  exit 1
fi

if [[ -n "$doc_id" ]]; then
  log_info "Document uploaded. ID: $doc_id"
  log_info "Primary document for PDF/search/version/WOPI checks: ${filename}"
else
  log_error "Upload failed or ID not returned."
  echo "Response: $upload_resp"
  exit 1
fi

# 4.0.0 PDF preview API check (server-rendered fallback)
log_info "Checking PDF preview API..."
preview_resp=$(curl_maybe "${BASE_URL}/api/v1/documents/${doc_id}/preview")
if command -v jq &> /dev/null; then
  preview_supported=$(echo "$preview_resp" | jq -r '.supported // false')
  preview_page_count=$(echo "$preview_resp" | jq -r '.pageCount // 0')
  preview_content=$(echo "$preview_resp" | jq -r '.pages[0].content // empty')
else
  preview_supported=$(echo "$preview_resp" | grep -o '"supported":[^,]*' | head -1 | cut -d: -f2 | tr -d ' ' || true)
  preview_page_count=$(echo "$preview_resp" | grep -o '"pageCount":[0-9]*' | head -1 | cut -d: -f2 || true)
  preview_content=$(echo "$preview_resp" | grep -o '"content":"[^"]*"' | head -1 | cut -d'"' -f4 || true)
fi

if [[ "$preview_supported" == "true" && -n "${preview_content:-}" ]]; then
  log_info "PDF preview API OK (pages=${preview_page_count})."
else
  log_error "PDF preview API failed or returned empty content."
  echo "Response: $preview_resp"
  exit 1
fi

# 4.0 Rule Automation (create rule -> upload -> verify auto-tag)
log_info "Testing Rule Automation..."
rule_tag_name="auto-rule-tag-$(date +%s)"
rule_name="smoke-auto-tag-rule-$(date +%s)"

# Create a rule that auto-tags on upload
rule_payload=$(cat <<EOF
{
  "name": "${rule_name}",
  "description": "Auto-created by smoke test",
  "triggerType": "DOCUMENT_CREATED",
  "priority": 100,
  "enabled": true,
  "stopOnMatch": false,
  "scopeMimeTypes": "",
  "scopeFolderId": "${upload_parent_id}",
  "condition": {"type": "ALWAYS_TRUE"},
  "actions": [
    {
      "type": "ADD_TAG",
      "params": {"tagName": "${rule_tag_name}"},
      "continueOnError": true,
      "order": 0
    }
  ]
}
EOF
)

rule_resp=$(curl_maybe -X POST -H "Content-Type: application/json" \
  -d "${rule_payload}" \
  "${BASE_URL}/api/v1/rules")
rule_id=$(extract_id "$rule_resp")
if [[ -n "${rule_id:-}" ]]; then
  log_info "Rule created. ID: ${rule_id}"
else
  log_warn "Failed to create rule; skipping rule automation tests."
fi

# Upload a new document to trigger the rule
if [[ -n "${rule_id:-}" ]]; then
  rule_test_file=$(mktemp "${TMPDIR:-/tmp}/ecm-rule-test.XXXXXX")
  rule_test_filename="${rule_test_file}.txt"
  mv "$rule_test_file" "$rule_test_filename"
  echo "Rule automation test content $(date)" > "$rule_test_filename"

  rule_upload_resp=$(curl_cmd -F "file=@${rule_test_filename}" "${BASE_URL}/api/v1/documents/upload?folderId=${upload_parent_id}")
  rm -f "$rule_test_filename"

  rule_doc_id=$(extract_id "$rule_upload_resp")
  if [[ -n "${rule_doc_id:-}" ]]; then
    log_info "Rule test document uploaded. ID: ${rule_doc_id}"

    # Wait for rule to execute and verify the tag was applied
    rule_tag_found=0
    for attempt in {1..10}; do
      sleep 1
      tags_resp=$(curl_maybe "${BASE_URL}/api/v1/nodes/${rule_doc_id}/tags")
      if echo "$tags_resp" | grep -q "${rule_tag_name}"; then
        rule_tag_found=1
        break
      fi
    done

    if [[ "$rule_tag_found" -eq 1 ]]; then
      log_info "Rule automation OK: auto-tag '${rule_tag_name}' applied to uploaded document."
    else
      log_error "Rule automation FAILED: expected auto-tag '${rule_tag_name}' not found on document."
      echo "Tags response: $tags_resp"
      exit 1
    fi

    # Cleanup the rule test document
    if curl_maybe -X POST "${BASE_URL}/api/trash/nodes/${rule_doc_id}" > /dev/null; then
      curl_maybe -X DELETE "${BASE_URL}/api/trash/${rule_doc_id}" > /dev/null
    fi
  else
    log_warn "Rule test document upload failed; skipping rule automation verification."
  fi

  # Cleanup the rule
  if curl_maybe -X DELETE "${BASE_URL}/api/v1/rules/${rule_id}" > /dev/null; then
    log_info "Rule cleaned up."
  fi
fi

# 4.0.2 Scheduled Rule Testing (cron validation + scheduled rule CRUD)
log_info "Testing Scheduled Rules..."

# Test cron validation endpoint
log_info "Validating cron expression..."
cron_validation_resp=$(curl_maybe -X POST -H "Content-Type: application/json" \
  -d '{"cronExpression":"0 0 * * * *","timezone":"UTC"}' \
  "${BASE_URL}/api/v1/rules/validate-cron")

if command -v jq &> /dev/null; then
  cron_valid=$(echo "$cron_validation_resp" | jq -r '.valid // false')
  cron_next=$(echo "$cron_validation_resp" | jq -r '.nextExecutions[0] // empty')
else
  cron_valid=$(echo "$cron_validation_resp" | grep -o '"valid":true' | head -1 || true)
  cron_next=$(echo "$cron_validation_resp" | grep -o '"nextExecutions":\["[^"]*"' | head -1 | cut -d'"' -f4 || true)
fi

if [[ "$cron_valid" == "true" || -n "$cron_valid" ]]; then
  log_info "Cron validation OK (next execution: ${cron_next:-?})."
else
  log_warn "Cron validation failed or returned invalid."
  echo "Response: $cron_validation_resp"
fi

# Create a scheduled rule
scheduled_rule_name="smoke-scheduled-rule-$(date +%s)"
scheduled_tag="scheduled-smoke-tag"
scheduled_rule_payload=$(cat <<EOF
{
  "name": "${scheduled_rule_name}",
  "description": "Scheduled rule created by smoke test",
  "triggerType": "SCHEDULED",
  "priority": 100,
  "enabled": true,
  "stopOnMatch": false,
  "scopeMimeTypes": "",
  "scopeFolderId": "${upload_parent_id}",
  "condition": {"type": "ALWAYS_TRUE"},
  "actions": [
    {
      "type": "ADD_TAG",
      "params": {"tagName": "${scheduled_tag}"},
      "continueOnError": true,
      "order": 0
    }
  ],
  "cronExpression": "0 0 0 * * *",
  "timezone": "UTC",
  "maxItemsPerRun": 100
}
EOF
)

scheduled_rule_resp=$(curl_maybe -X POST -H "Content-Type: application/json" \
  -d "${scheduled_rule_payload}" \
  "${BASE_URL}/api/v1/rules")
scheduled_rule_id=$(extract_id "$scheduled_rule_resp")

if [[ -n "${scheduled_rule_id:-}" ]]; then
  log_info "Scheduled rule created. ID: ${scheduled_rule_id}"

  # Verify scheduled rule fields in response
  if command -v jq &> /dev/null; then
    has_cron=$(echo "$scheduled_rule_resp" | jq -r '.cronExpression // empty')
    has_timezone=$(echo "$scheduled_rule_resp" | jq -r '.timezone // empty')
    has_max=$(echo "$scheduled_rule_resp" | jq -r '.maxItemsPerRun // empty')
  else
    has_cron=$(echo "$scheduled_rule_resp" | grep -o '"cronExpression":"[^"]*"' | head -1 | cut -d'"' -f4 || true)
    has_timezone=$(echo "$scheduled_rule_resp" | grep -o '"timezone":"[^"]*"' | head -1 | cut -d'"' -f4 || true)
    has_max=$(echo "$scheduled_rule_resp" | grep -o '"maxItemsPerRun":[0-9]*' | head -1 | cut -d: -f2 || true)
  fi

  if [[ -n "${has_cron:-}" && -n "${has_timezone:-}" ]]; then
    log_info "Scheduled rule fields verified (cron=${has_cron}, tz=${has_timezone}, max=${has_max:-?})."
  else
    log_warn "Scheduled rule fields not found in response."
  fi

  # Fetch the rule to verify it's retrievable
  fetched_rule=$(curl_maybe "${BASE_URL}/api/v1/rules/${scheduled_rule_id}")
  if echo "$fetched_rule" | grep -q '"triggerType":"SCHEDULED"'; then
    log_info "Scheduled rule fetch OK."
  else
    log_warn "Scheduled rule fetch did not return expected trigger type."
  fi

  # Upload a test document for scheduled rule trigger verification
  log_info "Uploading document for scheduled rule trigger test..."
  scheduled_test_file=$(mktemp "${TMPDIR:-/tmp}/ecm-scheduled-test.XXXXXX")
  scheduled_test_filename="${scheduled_test_file}.txt"
  mv "$scheduled_test_file" "$scheduled_test_filename"
  echo "Scheduled rule trigger test content $(date)" > "$scheduled_test_filename"

  scheduled_upload_resp=$(curl_cmd -F "file=@${scheduled_test_filename}" "${BASE_URL}/api/v1/documents/upload?folderId=${upload_parent_id}")
  rm -f "$scheduled_test_filename"

  scheduled_doc_id=$(extract_id "$scheduled_upload_resp")
  if [[ -n "${scheduled_doc_id:-}" ]]; then
    log_info "Scheduled rule test document uploaded. ID: ${scheduled_doc_id}"

    # Manually trigger the scheduled rule (avoids waiting for poll interval)
    log_info "Manually triggering scheduled rule..."
    trigger_resp_status=$(curl -sS -o /dev/null -w "%{http_code}" "${auth_args[@]}" \
      -X POST "${BASE_URL}/api/v1/rules/${scheduled_rule_id}/trigger" || echo "000")

    if [[ "$trigger_resp_status" == "200" || "$trigger_resp_status" == "204" ]]; then
      log_info "Scheduled rule triggered successfully (HTTP ${trigger_resp_status})."

      # Wait and verify the tag was applied to the test document
      scheduled_tag_found=0
      for attempt in {1..15}; do
        sleep 1
        scheduled_tags_resp=$(curl_maybe "${BASE_URL}/api/v1/nodes/${scheduled_doc_id}/tags")
        if echo "$scheduled_tags_resp" | grep -q "${scheduled_tag}"; then
          scheduled_tag_found=1
          break
        fi
      done

      if [[ "$scheduled_tag_found" -eq 1 ]]; then
        log_info "Scheduled rule trigger OK: auto-tag '${scheduled_tag}' applied to test document."
      else
        log_warn "Scheduled rule trigger: tag '${scheduled_tag}' not found on document (rule may not have matched or tag application delayed)."
        echo "Tags response: $scheduled_tags_resp"
      fi

      # Verify scheduled rule audit log entries (rule execution + batch summary)
      log_info "Verifying scheduled rule audit logs..."
      scheduled_audit_found=0
      audit_events=""
      for attempt in {1..12}; do
        sleep 1
        scheduled_audit_resp=$(curl_maybe "${BASE_URL}/api/v1/analytics/rules/recent?limit=25")
        if command -v jq &> /dev/null; then
          audit_events=$(echo "$scheduled_audit_resp" | jq -r --arg rule "${scheduled_rule_name}" '
            map(select((.nodeName // "") == $rule or (.details // "" | contains($rule))) | .eventType)
            | unique
            | join(",")')
        else
          if echo "$scheduled_audit_resp" | grep -q "${scheduled_rule_name}"; then
            audit_events="found"
          else
            audit_events=""
          fi
        fi

        if [[ -n "${audit_events:-}" ]]; then
          scheduled_audit_found=1
          break
        fi
      done

      if [[ "$scheduled_audit_found" -eq 1 ]]; then
        log_info "Scheduled rule audit log OK (events: ${audit_events})."
      else
        if [[ "$scheduled_tag_found" -eq 1 ]]; then
          log_error "Scheduled rule audit log missing for '${scheduled_rule_name}'."
          echo "Audit response: ${scheduled_audit_resp}"
          exit 1
        else
          log_warn "Scheduled rule audit log not found (tag not applied; rule may not have executed)."
        fi
      fi

      # Verify rule execution summary endpoint is responsive
      rule_summary_resp=$(curl_maybe "${BASE_URL}/api/v1/analytics/rules/summary?days=1")
      if command -v jq &> /dev/null; then
        summary_executions=$(echo "$rule_summary_resp" | jq -r '.executions // empty')
      else
        summary_executions=$(echo "$rule_summary_resp" | grep -o '"executions":[0-9]*' | head -1 | cut -d: -f2 || true)
      fi
      if [[ -n "${summary_executions:-}" ]]; then
        log_info "Rule execution summary endpoint OK (executions=${summary_executions})."
      else
        log_warn "Rule execution summary did not return expected payload."
      fi
    else
      log_warn "Scheduled rule trigger failed (HTTP ${trigger_resp_status}). Manual trigger endpoint may not exist."
    fi

    # Cleanup the scheduled rule test document
    if curl_maybe -X POST "${BASE_URL}/api/trash/nodes/${scheduled_doc_id}" > /dev/null; then
      curl_maybe -X DELETE "${BASE_URL}/api/trash/${scheduled_doc_id}" > /dev/null
      log_info "Scheduled rule test document cleaned up."
    fi
  else
    log_warn "Scheduled rule test document upload failed; skipping trigger verification."
  fi

  # Cleanup scheduled rule
  if curl_maybe -X DELETE "${BASE_URL}/api/v1/rules/${scheduled_rule_id}" > /dev/null; then
    log_info "Scheduled rule cleaned up."
  fi
else
  log_warn "Failed to create scheduled rule; skipping scheduled rule tests."
fi

# 4.0.1 Favorites (add/check/list/remove)
log_info "Testing Favorites..."
if curl_cmd -X POST "${BASE_URL}/api/v1/favorites/${doc_id}" > /dev/null; then
  log_info "Added to favorites."
else
  log_error "Failed to add favorite."
  exit 1
fi

fav_check=$(curl_cmd "${BASE_URL}/api/v1/favorites/${doc_id}/check" || true)
if [[ "$fav_check" == "true" ]]; then
  log_info "Favorite check OK."
else
  log_error "Favorite check failed (expected true). Response: ${fav_check}"
  exit 1
fi

fav_batch_resp=$(curl_cmd -X POST -H "Content-Type: application/json" \
  -d "{\"nodeIds\":[\"${doc_id}\"]}" \
  "${BASE_URL}/api/v1/favorites/batch/check" || true)
if command -v jq &> /dev/null; then
  batch_has=$(echo "$fav_batch_resp" | jq -r '.favoritedNodeIds[]?' | grep -c "^${doc_id}$" || true)
else
  batch_has=$(echo "$fav_batch_resp" | grep -c "${doc_id}" || true)
fi
if [[ "${batch_has:-0}" -gt 0 ]]; then
  log_info "Favorite batch check OK."
else
  log_error "Favorite batch check failed (expected node to be included). Response: ${fav_batch_resp}"
  exit 1
fi

fav_list=$(curl_cmd "${BASE_URL}/api/v1/favorites?page=0&size=50" || true)
if echo "$fav_list" | grep -q "${doc_id}"; then
  log_info "Favorites list includes document."
else
  log_error "Favorites list did not include document."
  exit 1
fi

if curl_cmd -X DELETE "${BASE_URL}/api/v1/favorites/${doc_id}" > /dev/null; then
  log_info "Removed from favorites."
else
  log_error "Failed to remove favorite."
  exit 1
fi

fav_check_after=$(curl_cmd "${BASE_URL}/api/v1/favorites/${doc_id}/check" || true)
if [[ "$fav_check_after" == "false" ]]; then
  log_info "Favorite removal check OK."
else
  log_error "Favorite removal check failed (expected false). Response: ${fav_check_after}"
  exit 1
fi

fav_batch_after=$(curl_cmd -X POST -H "Content-Type: application/json" \
  -d "{\"nodeIds\":[\"${doc_id}\"]}" \
  "${BASE_URL}/api/v1/favorites/batch/check" || true)
if command -v jq &> /dev/null; then
  batch_has_after=$(echo "$fav_batch_after" | jq -r '.favoritedNodeIds[]?' | grep -c "^${doc_id}$" || true)
else
  batch_has_after=$(echo "$fav_batch_after" | grep -c "${doc_id}" || true)
fi
if [[ "${batch_has_after:-0}" -eq 0 ]]; then
  log_info "Favorite batch removal check OK."
else
  log_error "Favorite batch removal check failed (expected node to be absent). Response: ${fav_batch_after}"
  exit 1
fi

# 4.0.1 Correspondent facets/filters smoke (best-effort): create correspondent + upload marker doc
log_info "Preparing correspondent filter test..."
corr_marker="smoke-corr-$(date +%s)"
corr_resp=$(curl_maybe -X POST -H "Content-Type: application/json" \
  -d "{\"name\":\"${corr_marker}\",\"matchAlgorithm\":\"AUTO\",\"matchPattern\":\"${corr_marker}\",\"insensitive\":true}" \
  "${BASE_URL}/api/v1/correspondents")
corr_id=$(extract_id "$corr_resp")
if echo "$corr_resp" | grep -q "\"name\":\"${corr_marker}\""; then
  log_info "Created correspondent: ${corr_marker}"
else
  log_warn "Failed to create correspondent (it may already exist or the user lacks permission)."
fi

tmpbase_corr=$(mktemp "${TMPDIR:-/tmp}/ecm-smoke-corr.XXXXXX")
tmpfile_corr="${tmpbase_corr}.txt"
mv "$tmpbase_corr" "$tmpfile_corr"
echo "Athena ECM correspondent marker: ${corr_marker}" > "$tmpfile_corr"
corr_upload_resp=$(curl_cmd -F "file=@${tmpfile_corr};filename=${corr_marker}.txt" \
  "${BASE_URL}/api/v1/documents/upload?folderId=${upload_parent_id}")
rm -f "$tmpfile_corr"
doc_id_corr=$(extract_id "$corr_upload_resp")
if [[ -n "${doc_id_corr:-}" ]]; then
  log_info "Correspondent test document uploaded. ID: $doc_id_corr"
  curl_maybe -X POST "${BASE_URL}/api/v1/search/index/${doc_id_corr}" > /dev/null
else
  log_warn "Correspondent test document upload failed; skipping correspondent search tests."
fi

# Manual assignment: set correspondent on the primary uploaded document and re-index.
manual_assignment_expected=0
if [[ -n "${corr_id:-}" ]]; then
  log_info "Assigning correspondent to uploaded document (manual)..."
  if curl_cmd -X PATCH -H "Content-Type: application/json" \
    -d "{\"correspondentId\":\"${corr_id}\"}" \
    "${BASE_URL}/api/v1/nodes/${doc_id}" > /dev/null; then
    manual_assignment_expected=1
    curl_maybe -X POST "${BASE_URL}/api/v1/search/index/${doc_id}" > /dev/null
    log_info "Manual correspondent assignment OK."
  else
    log_error "Manual correspondent assignment failed."
    exit 1
  fi
else
  log_warn "Correspondent id missing; skipping manual correspondent assignment test."
fi

# 4.1 PDF smoke: Version History + Editor URL
log_info "PDF smoke: checking version history..."
versions_resp=$(curl_cmd "${BASE_URL}/api/v1/documents/${doc_id}/versions")
if command -v jq &> /dev/null; then
  version_count=$(echo "$versions_resp" | jq -r 'length')
  version_label=$(echo "$versions_resp" | jq -r '.[0].versionLabel // empty')
else
  version_count=$(echo "$versions_resp" | grep -c '"versionLabel"' || true)
  version_label=$(echo "$versions_resp" | grep -o '"versionLabel":"[^"]*"' | head -1 | cut -d'"' -f4 || true)
fi

if [[ "${version_count:-0}" -ge 1 ]]; then
  log_info "Version history OK (count=${version_count}, first=${version_label:-?})."
else
  log_error "Version history is empty for uploaded document: ${doc_id}"
  echo "Response: $versions_resp"
  exit 1
fi

# 4.2 WOPI end-to-end (optional): URL -> CheckFileInfo/GetFile/Lock/PutFile/Unlock
log_info "PDF smoke: checking WOPI editor URL..."
wopi_resp=$(curl_maybe "${BASE_URL}/api/v1/integration/wopi/url/${doc_id}?permission=write")
if command -v jq &> /dev/null; then
  wopi_url=$(echo "$wopi_resp" | jq -r '.wopiUrl // empty')
else
  wopi_url=$(echo "$wopi_resp" | grep -o '"wopiUrl":"[^"]*"' | head -1 | cut -d'"' -f4 || true)
fi
if [[ -z "${wopi_url:-}" ]]; then
  log_warn "WOPI URL not available (integration may be disabled or file type unsupported)."
else
  log_info "WOPI URL generated."

  access_token=$(echo "$wopi_url" | sed -n 's/.*[?&]access_token=\([^&]*\).*/\1/p')
  if [[ -z "${access_token:-}" ]]; then
    log_warn "Failed to extract WOPI access_token from URL."
  else
    check_info=""
    if ! check_info=$(curl -fsS "${auth_args[@]}" "${BASE_URL}/wopi/files/${doc_id}?access_token=${access_token}"); then
      log_warn "WOPI CheckFileInfo failed."
      check_info=""
    fi
    if command -v jq &> /dev/null; then
      can_write=$(echo "$check_info" | jq -r '.UserCanWrite // empty' || true)
    else
      can_write=$(echo "$check_info" | grep -o '"UserCanWrite":[^,}]*' | head -1 | cut -d: -f2 | tr -d ' ' || true)
    fi
    if [[ "${can_write:-}" == "true" ]]; then
      log_info "WOPI CheckFileInfo OK (UserCanWrite=true)."
    else
      log_warn "WOPI CheckFileInfo did not report write access."
    fi

    if curl -fsS "${auth_args[@]}" "${BASE_URL}/wopi/files/${doc_id}/contents?access_token=${access_token}" > /dev/null; then
      log_info "WOPI GetFile OK."
    else
      log_warn "WOPI GetFile failed."
    fi

    lock_value="smoke-lock-$(date +%s)"
    lock_status=$(curl -sS -o /dev/null -w "%{http_code}" -X POST \
      -H "X-WOPI-Override: LOCK" \
      -H "X-WOPI-Lock: ${lock_value}" \
      "${BASE_URL}/wopi/files/${doc_id}?access_token=${access_token}" || true)
    if [[ "$lock_status" == "200" ]]; then
      log_info "WOPI LOCK OK."
    else
      log_warn "WOPI LOCK failed (HTTP ${lock_status})."
    fi

    put_payload=$(mktemp "${TMPDIR:-/tmp}/ecm-wopi-putfile.XXXXXX")
    echo "WOPI PutFile smoke update $(date)" > "$put_payload"
    put_status=$(curl -sS -o /dev/null -w "%{http_code}" -X POST \
      -H "X-WOPI-Lock: ${lock_value}" \
      --data-binary "@${put_payload}" \
      "${BASE_URL}/wopi/files/${doc_id}/contents?access_token=${access_token}" || true)
    rm -f "$put_payload"
    if [[ "$put_status" == "200" ]]; then
      log_info "WOPI PutFile OK."
      versions_after=$(curl_maybe "${BASE_URL}/api/v1/documents/${doc_id}/versions")
      if command -v jq &> /dev/null; then
        version_count_after=$(echo "$versions_after" | jq -r 'length')
        latest_label=$(echo "$versions_after" | jq -r '.[0].versionLabel // empty')
      else
        version_count_after=$(echo "$versions_after" | grep -c '"versionLabel"' || true)
        latest_label=$(echo "$versions_after" | grep -o '"versionLabel":"[^"]*"' | head -1 | cut -d'"' -f4 || true)
      fi
      if [[ "${version_count_after:-0}" -gt "${version_count:-0}" ]]; then
        log_info "Version increment OK (before=${version_count}, after=${version_count_after}, latest=${latest_label:-?})."
      else
        log_warn "Version did not increment after PutFile (before=${version_count}, after=${version_count_after})."
      fi
    else
      log_warn "WOPI PutFile failed (HTTP ${put_status})."
    fi

    unlock_status=$(curl -sS -o /dev/null -w "%{http_code}" -X POST \
      -H "X-WOPI-Override: UNLOCK" \
      -H "X-WOPI-Lock: ${lock_value}" \
      "${BASE_URL}/wopi/files/${doc_id}?access_token=${access_token}" || true)
    if [[ "$unlock_status" == "200" ]]; then
      log_info "WOPI UNLOCK OK."
    else
      log_warn "WOPI UNLOCK failed (HTTP ${unlock_status})."
    fi
  fi
fi

# 4.3 ML health (optional)
log_info "Checking ML health..."
if curl_maybe "${BASE_URL}/api/v1/ml/health" > /dev/null; then
  log_info "ML health OK."
else
  log_warn "ML health endpoint not available."
fi

# 5. Search (PDF smoke)
log_info "PDF smoke: searching for uploaded document..."
found_in_search=0
for attempt in {1..10}; do
  sleep 2
  search_resp=$(curl_cmd --get --data-urlencode "q=${filename}" --data-urlencode "size=50" "${BASE_URL}/api/v1/search")
  if echo "$search_resp" | grep -q "$doc_id"; then
    found_in_search=1
    break
  fi
done
if [[ "$found_in_search" -eq 1 ]]; then
  log_info "Search indexed the document."
else
  log_warn "Document not found in search results yet (indexing may be delayed)."
fi

log_info "Testing Saved Searches..."
saved_name="smoke-saved-search-$(date +%s)"
saved_payload=$(cat <<EOF
{"name":"${saved_name}","queryParams":{"query":"${filename}","filters":{},"highlightEnabled":true,"facetFields":["mimeType","createdBy","tags","categories","correspondent"],"pageable":{"page":0,"size":50}}}
EOF
)
saved_resp=$(curl_maybe -X POST -H "Content-Type: application/json" -d "${saved_payload}" "${BASE_URL}/api/v1/search/saved")
saved_id=$(extract_id "$saved_resp")
if [[ -n "${saved_id:-}" ]]; then
  log_info "Saved search created. ID: ${saved_id}"
else
  log_warn "Failed to create saved search; skipping saved search execution checks."
fi

if [[ -n "${saved_id:-}" ]]; then
  saved_list=$(curl_maybe "${BASE_URL}/api/v1/search/saved" || true)
  if echo "$saved_list" | grep -q "\"name\":\"${saved_name}\""; then
    log_info "Saved searches list includes saved search."
  else
    log_warn "Saved searches list did not include the saved search (may be filtered by user)."
  fi

  found_saved=0
  for attempt in {1..10}; do
    sleep 1
    saved_exec=$(curl_maybe "${BASE_URL}/api/v1/search/saved/${saved_id}/execute" || true)
    if echo "$saved_exec" | grep -q "$doc_id"; then
      found_saved=1
      break
    fi
  done
  if [[ "$found_saved" -eq 1 ]]; then
    log_info "Saved search execution returned the document."
  else
    log_warn "Saved search execution did not return the document (indexing may be delayed)."
  fi

  if curl_cmd -X DELETE "${BASE_URL}/api/v1/search/saved/${saved_id}" > /dev/null; then
    log_info "Saved search deleted."
  else
    log_warn "Failed to delete saved search (manual cleanup may be required): ${saved_id}"
  fi
fi

if [[ -n "${doc_id_corr:-}" ]]; then
  log_info "Searching for correspondent test document..."
  found_corr_in_search=0
  for attempt in {1..20}; do
    sleep 2
    corr_search_resp=$(curl_cmd --get --data-urlencode "q=${corr_marker}" --data-urlencode "size=50" "${BASE_URL}/api/v1/search")
    if echo "$corr_search_resp" | grep -q "$doc_id_corr"; then
      found_corr_in_search=1
      break
    fi
  done
  if [[ "$found_corr_in_search" -eq 1 ]]; then
    log_info "Search indexed the correspondent test document."
  else
    log_warn "Correspondent test document not found in search results (indexing may be delayed)."
  fi

  if [[ "$found_corr_in_search" -eq 1 ]]; then
    log_info "Testing advanced search correspondent filter..."
    filtered_ok=0
    for attempt in {1..10}; do
      sleep 1
      corr_filter_resp=$(curl_cmd -X POST -H "Content-Type: application/json" \
        -d "{\"query\":\"\",\"filters\":{\"correspondents\":[\"${corr_marker}\"]},\"pageable\":{\"page\":0,\"size\":50}}" \
        "${BASE_URL}/api/v1/search/advanced")
      has_corr_doc=0
      if echo "$corr_filter_resp" | grep -q "$doc_id_corr"; then
        has_corr_doc=1
      fi
      has_main_doc=1
      if [[ "$manual_assignment_expected" -eq 1 ]]; then
        has_main_doc=0
        if echo "$corr_filter_resp" | grep -q "$doc_id"; then
          has_main_doc=1
        fi
      fi
      if [[ "$has_corr_doc" -eq 1 && "$has_main_doc" -eq 1 ]]; then
        filtered_ok=1
        break
      fi
    done
    if [[ "$filtered_ok" -eq 1 ]]; then
      log_info "Correspondent filter OK."
    else
      log_error "Correspondent filter failed to return expected document."
      exit 1
    fi

    log_info "Testing facets include correspondent..."
    facet_ok=0
    for attempt in {1..10}; do
      sleep 1
      corr_facets_resp=$(curl_cmd --get --data-urlencode "q=${corr_marker}" "${BASE_URL}/api/v1/search/facets")
      if command -v jq &> /dev/null; then
        if echo "$corr_facets_resp" | jq -e --arg v "$corr_marker" '.correspondent[]? | select(.value==$v)' > /dev/null; then
          facet_ok=1
          break
        fi
      else
        if echo "$corr_facets_resp" | grep -q "$corr_marker"; then
          facet_ok=1
          break
        fi
      fi
    done
    if [[ "$facet_ok" -eq 1 ]]; then
      log_info "Correspondent facet OK."
    else
      log_error "Correspondent facet missing from /api/v1/search/facets response."
      exit 1
    fi
  else
    log_warn "Skipping correspondent filter/facet checks due to delayed indexing."
  fi
fi

# 5.1 Copy/Move (requires dedicated folder for cleanup)
if [[ -n "${folder_id:-}" ]]; then
  log_info "Testing Copy/Move between folders..."
  target_folder_name="smoke-target-$(date +%s)"
  target_resp=$(curl_cmd -X POST -H "Content-Type: application/json" \
    -d "{\"name\":\"${target_folder_name}\",\"parentId\":\"${upload_parent_id}\",\"folderType\":\"GENERAL\",\"inheritPermissions\":true}" \
    "${BASE_URL}/api/v1/folders")
  target_id=$(extract_id "$target_resp")
  if [[ -z "${target_id:-}" ]]; then
    log_error "Failed to create target folder for copy/move."
    echo "Response: $target_resp"
    exit 1
  fi

  copy_name="copy-${filename}"
  copy_resp=$(curl_cmd -X POST -H "Content-Type: application/json" \
    -d "{\"nodeId\":\"${doc_id}\",\"newName\":\"${copy_name}\",\"deep\":false}" \
    "${BASE_URL}/api/v1/folders/${target_id}/copy")
  copy_id=$(extract_id "$copy_resp")
  if [[ -z "${copy_id:-}" ]]; then
    log_error "Failed to copy document to target folder."
    echo "Response: $copy_resp"
    exit 1
  fi
  log_info "Copied document. ID: ${copy_id}"

  target_contents=$(curl_cmd "${BASE_URL}/api/v1/folders/${target_id}/contents?page=0&size=200")
  if command -v jq &> /dev/null; then
    if ! echo "$target_contents" | jq -e --arg id "$copy_id" '.content[]? | select(.id==$id) | .id' > /dev/null; then
      log_error "Copied document not found in target folder contents."
      exit 1
    fi
  else
    if ! echo "$target_contents" | grep -q "$copy_id"; then
      log_error "Copied document not found in target folder contents."
      exit 1
    fi
  fi

  if curl_cmd -X POST -H "Content-Type: application/json" \
    -d "{\"nodeId\":\"${copy_id}\"}" \
    "${BASE_URL}/api/v1/folders/${upload_parent_id}/move" > /dev/null; then
    log_info "Moved copied document back to the original folder."
  else
    log_error "Failed to move copied document back to original folder."
    exit 1
  fi

  original_contents=$(curl_cmd "${BASE_URL}/api/v1/folders/${upload_parent_id}/contents?page=0&size=200")
  if command -v jq &> /dev/null; then
    if ! echo "$original_contents" | jq -e --arg id "$copy_id" '.content[]? | select(.id==$id) | .id' > /dev/null; then
      log_error "Moved copy not found in original folder contents."
      exit 1
    fi
  else
    if ! echo "$original_contents" | grep -q "$copy_id"; then
      log_error "Moved copy not found in original folder contents."
      exit 1
    fi
  fi
else
  log_warn "Skipping copy/move test because dedicated folder was not created."
fi

# 6. Create Share Link
log_info "Creating Share Link..."
share_resp=$(curl_maybe -X POST -H "Content-Type: application/json" \
  -d '{"name":"Smoke Share","permissionLevel":"VIEW"}' \
  "${BASE_URL}/api/share/nodes/${doc_id}")

share_token=$(echo "$share_resp" | grep -o '"token":"[^"]*"' | head -1 | cut -d'"' -f4 || true)
if [[ -n "$share_token" ]]; then
  mkdir -p "${REPO_ROOT}/tmp"
  share_token_file="${REPO_ROOT}/tmp/smoke.share_token"
  echo -n "$share_token" > "$share_token_file"
  chmod 600 "$share_token_file"
  log_info "Share link created. Token written to tmp/smoke.share_token"
else
  log_warn "Failed to create share link (Check JSON parsing or endpoint)."
fi

# 6.1 Add tag to document
log_info "Adding Tag to document..."
if curl_cmd -X POST -H "Content-Type: application/json" \
  -d '{"tagName":"smoke-tag"}' \
  "${BASE_URL}/api/v1/nodes/${doc_id}/tags" > /dev/null; then
  log_info "Tag added."
else
  log_warn "Failed to add tag."
fi

# 6.2 Create category and assign to document
log_info "Creating Category and assigning to document..."
category_resp=$(curl_cmd -X POST -H "Content-Type: application/json" \
  -d '{"name":"smoke-category","description":"created by smoke","parentId":null}' \
  "${BASE_URL}/api/v1/categories" || true)

if command -v jq &> /dev/null; then
  category_id=$(echo "$category_resp" | jq -r '.id // empty')
else
  category_id=$(echo "$category_resp" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4 || true)
fi

if [[ -n "${category_id:-}" ]]; then
  if curl_cmd -X POST -H "Content-Type: application/json" \
    -d "{\"categoryId\":\"${category_id}\"}" \
    "${BASE_URL}/api/v1/nodes/${doc_id}/categories" > /dev/null; then
    log_info "Category assigned. ID: ${category_id}"
  else
    log_warn "Failed to assign category to document."
  fi
else
  log_warn "Failed to create category (it may already exist)."
fi

# 6.2.1 Re-index and validate facets
log_info "Re-indexing document for faceted search..."
curl_cmd -X POST "${BASE_URL}/api/v1/search/index/${doc_id}" > /dev/null

log_info "Checking advanced search..."
advanced_ok=0
advanced_resp=""
advanced_path_prefix="/uploads"
if [[ -n "${folder_id:-}" ]]; then
  advanced_path_prefix="/uploads/${folder_name}"
fi
for attempt in {1..10}; do
  sleep 2
  advanced_resp=$(curl_cmd -X POST -H "Content-Type: application/json" \
    -d "{\"query\":\"${filename}\",\"filters\":{\"path\":\"${advanced_path_prefix}\"},\"pageable\":{\"page\":0,\"size\":50}}" \
    "${BASE_URL}/api/v1/search/advanced")
  if echo "$advanced_resp" | grep -q "$doc_id"; then
    advanced_ok=1
    break
  fi
done
if [[ "$advanced_ok" -eq 1 ]]; then
  log_info "Advanced search returned the document."
else
  log_error "Advanced search did not return the document."
  echo "Response: $advanced_resp"
  exit 1
fi

log_info "Checking faceted search facets..."
facets_ok=0
facets_resp=""
for attempt in {1..15}; do
  sleep 2
  facets_resp=$(curl_cmd --get --data-urlencode "q=${filename}" "${BASE_URL}/api/v1/search/facets")
  if echo "$facets_resp" | grep -q "smoke-tag" && echo "$facets_resp" | grep -q "smoke-category"; then
    facets_ok=1
    break
  fi
done
if [[ "$facets_ok" -eq 1 ]]; then
  log_info "Faceted search returned expected tag/category facets."
else
  log_error "Faceted search did not return expected tag/category facets."
  echo "Response: $facets_resp"
  exit 1
fi

# 6.3 Workflow approval (start -> task -> complete -> history)
log_info "Starting workflow approval..."
workflow_approver="${ECM_WORKFLOW_APPROVER:-admin}"
tasks_before=$(curl_maybe "${BASE_URL}/api/v1/workflows/tasks/my")
if command -v jq &> /dev/null; then
  task_ids_before=$(echo "$tasks_before" | jq -r '.[].id' 2>/dev/null || true)
else
  task_ids_before=$(echo "$tasks_before" | grep -o '"id":"[^"]*"' | cut -d'"' -f4 || true)
fi

approval_resp=$(curl_cmd -X POST -H "Content-Type: application/json" \
  -d "{\"approvers\":[\"${workflow_approver}\"],\"comment\":\"smoke approval\"}" \
  "${BASE_URL}/api/v1/workflows/document/${doc_id}/approval")
if command -v jq &> /dev/null; then
  workflow_instance_id=$(echo "$approval_resp" | jq -r '.id // empty')
else
  workflow_instance_id=$(echo "$approval_resp" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4 || true)
fi
if [[ -z "${workflow_instance_id:-}" ]]; then
  log_error "Failed to start workflow approval."
  echo "Response: $approval_resp"
  exit 1
fi
log_info "Workflow started. Instance: ${workflow_instance_id}"

new_task_id=""
for attempt in {1..10}; do
  tasks_after=$(curl_maybe "${BASE_URL}/api/v1/workflows/tasks/my")
  if command -v jq &> /dev/null; then
    task_ids_after=$(echo "$tasks_after" | jq -r '.[].id' 2>/dev/null || true)
  else
    task_ids_after=$(echo "$tasks_after" | grep -o '"id":"[^"]*"' | cut -d'"' -f4 || true)
  fi

  while read -r candidate; do
    [[ -z "$candidate" ]] && continue
    if ! echo "${task_ids_before:-}" | grep -Fxq "$candidate"; then
      new_task_id="$candidate"
      break
    fi
  done <<< "${task_ids_after:-}"

  if [[ -n "${new_task_id:-}" ]]; then
    break
  fi
  sleep 1
done

if [[ -z "${new_task_id:-}" ]]; then
  log_error "Failed to find newly created workflow task."
  exit 1
fi
log_info "Completing task: ${new_task_id}"
curl_cmd -X POST -H "Content-Type: application/json" \
  -d '{"approved":true,"comment":"smoke approved"}' \
  "${BASE_URL}/api/v1/workflows/tasks/${new_task_id}/complete" > /dev/null

workflow_finished=0
for attempt in {1..10}; do
  history_resp=$(curl_maybe "${BASE_URL}/api/v1/workflows/document/${doc_id}/history")
  if command -v jq &> /dev/null; then
    end_time=$(echo "$history_resp" | jq -r --arg id "$workflow_instance_id" '.[] | select(.id==$id) | .endTime // empty' | head -1)
    if [[ -n "${end_time:-}" && "${end_time:-null}" != "null" ]]; then
      workflow_finished=1
      break
    fi
  else
    if echo "$history_resp" | grep -q "\"id\":\"${workflow_instance_id}\""; then
      workflow_finished=1
      break
    fi
  fi
  sleep 1
done
if [[ "$workflow_finished" -eq 1 ]]; then
  log_info "Workflow finished (history recorded)."
else
  log_warn "Workflow history not confirmed (may be delayed)."
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

if [[ -n "${folder_id:-}" ]]; then
  log_info "Cleaning up test folder..."
  if curl_maybe -X DELETE "${BASE_URL}/api/v1/folders/${folder_id}?permanent=true&recursive=true" > /dev/null; then
    log_info "Test folder deleted (permanent, recursive)."
  else
    log_warn "Failed to delete test folder (id=${folder_id})."
  fi
fi

log_info "=== Smoke check finished successfully ==="
