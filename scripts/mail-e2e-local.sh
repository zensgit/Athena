#!/usr/bin/env bash
set -euo pipefail

API_URL="${ECM_API_URL:-http://localhost:7700}"
KC_URL="${ECM_KEYCLOAK_URL:-http://localhost:8180}"
ADMIN_USER="${ECM_E2E_USERNAME:-admin}"
ADMIN_PASS="${ECM_E2E_PASSWORD:-admin}"

MAIL_IMAP_HOST="${MAIL_IMAP_HOST:-greenmail}"
MAIL_IMAP_PORT="${MAIL_IMAP_PORT:-3143}"
MAIL_SMTP_HOST="${MAIL_SMTP_HOST:-localhost}"
MAIL_SMTP_PORT="${MAIL_SMTP_PORT:-3025}"
MAIL_E2E_USER="${MAIL_E2E_USER:-mailuser@local.test}"
MAIL_E2E_PASS="${MAIL_E2E_PASS:-mailpass}"
MAIL_FROM="${MAIL_FROM:-sender@local.test}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-0}"

for bin in curl jq python3; do
  if ! command -v "${bin}" >/dev/null 2>&1; then
    echo "Missing dependency: ${bin}" >&2
    exit 1
  fi
done

TOKEN=""
FOLDER_ID=""
TAG_ID=""
ACCOUNT_ID=""
RULE_ID=""
DOC_ID=""
ATTACH_PATH=""
ATTACH_NAME=""

cleanup() {
  if [[ "${KEEP_ARTIFACTS}" != "1" && -n "${TOKEN}" ]]; then
    if [[ -n "${RULE_ID}" ]]; then
      curl -s -X DELETE "${API_URL}/api/v1/integration/mail/rules/${RULE_ID}" \
        -H "Authorization: Bearer ${TOKEN}" >/dev/null || true
    fi
    if [[ -n "${ACCOUNT_ID}" ]]; then
      curl -s -X DELETE "${API_URL}/api/v1/integration/mail/accounts/${ACCOUNT_ID}" \
        -H "Authorization: Bearer ${TOKEN}" >/dev/null || true
    fi
    if [[ -n "${TAG_ID}" ]]; then
      curl -s -X DELETE "${API_URL}/api/v1/tags/${TAG_ID}" \
        -H "Authorization: Bearer ${TOKEN}" >/dev/null || true
    fi
    if [[ -n "${FOLDER_ID}" ]]; then
      curl -s -X DELETE "${API_URL}/api/v1/folders/${FOLDER_ID}?permanent=true&recursive=true" \
        -H "Authorization: Bearer ${TOKEN}" >/dev/null || true
    fi
  fi

  if [[ -n "${ATTACH_PATH}" && -f "${ATTACH_PATH}" ]]; then
    rm -f "${ATTACH_PATH}"
  fi
}
trap cleanup EXIT

TOKEN=$(curl -s -X POST "${KC_URL}/realms/ecm/protocol/openid-connect/token" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'client_id=unified-portal' \
  -d 'grant_type=password' \
  -d "username=${ADMIN_USER}" \
  -d "password=${ADMIN_PASS}" | jq -r '.access_token')

if [[ -z "${TOKEN}" || "${TOKEN}" == "null" ]]; then
  echo "Failed to obtain access token" >&2
  exit 1
fi

ROOT_ID=$(curl -s -H "Authorization: Bearer ${TOKEN}" "${API_URL}/api/v1/folders/roots" \
  | jq -r 'map(select(.name=="Root" or .path=="/Root" or (.folderType|ascii_upcase)=="SYSTEM"))[0].id // .[0].id')

if [[ -z "${ROOT_ID}" || "${ROOT_ID}" == "null" ]]; then
  echo "Failed to resolve root folder id" >&2
  exit 1
fi

TS="$(date +%s)"
FOLDER_NAME="mail-e2e-${TS}"
TAG_NAME="mail-e2e-tag-${TS}"
SUBJECT="MailE2E-${TS}"

FOLDER_ID=$(curl -s -f -X POST "${API_URL}/api/v1/folders" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"${FOLDER_NAME}\",\"parentId\":\"${ROOT_ID}\"}" | jq -r '.id')

if [[ -z "${FOLDER_ID}" || "${FOLDER_ID}" == "null" ]]; then
  echo "Folder creation failed" >&2
  exit 1
fi

TAG_ID=$(curl -s -f -X POST "${API_URL}/api/v1/tags" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"${TAG_NAME}\"}" | jq -r '.id')

if [[ -z "${TAG_ID}" || "${TAG_ID}" == "null" ]]; then
  echo "Tag creation failed" >&2
  exit 1
fi

ACCOUNT_ID=$(curl -s -f -X POST "${API_URL}/api/v1/integration/mail/accounts" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"mail-e2e-account-${TS}\",\"host\":\"${MAIL_IMAP_HOST}\",\"port\":${MAIL_IMAP_PORT},\"username\":\"${MAIL_E2E_USER}\",\"password\":\"${MAIL_E2E_PASS}\",\"security\":\"NONE\",\"enabled\":true,\"pollIntervalMinutes\":1}" | jq -r '.id')

if [[ -z "${ACCOUNT_ID}" || "${ACCOUNT_ID}" == "null" ]]; then
  echo "Mail account creation failed" >&2
  exit 1
fi

RULE_ID=$(curl -s -f -X POST "${API_URL}/api/v1/integration/mail/rules" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"mail-e2e-rule-${TS}\",\"accountId\":\"${ACCOUNT_ID}\",\"priority\":1,\"subjectFilter\":\"${SUBJECT}\",\"actionType\":\"ATTACHMENTS_ONLY\",\"assignTagId\":\"${TAG_ID}\",\"assignFolderId\":\"${FOLDER_ID}\"}" | jq -r '.id')

if [[ -z "${RULE_ID}" || "${RULE_ID}" == "null" ]]; then
  echo "Mail rule creation failed" >&2
  exit 1
fi

ATTACH_PATH=$(mktemp -t mail-e2e-XXXXXX.txt)
ATTACH_NAME=$(basename "${ATTACH_PATH}")
echo "Mail automation attachment ${TS}" > "${ATTACH_PATH}"

MAIL_TO="${MAIL_E2E_USER}" MAIL_FROM="${MAIL_FROM}" MAIL_SUBJECT="${SUBJECT}" MAIL_ATTACH="${ATTACH_PATH}" \
MAIL_SMTP_HOST="${MAIL_SMTP_HOST}" MAIL_SMTP_PORT="${MAIL_SMTP_PORT}" \
python3 - <<'PY'
import os
import smtplib
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
from email.mime.base import MIMEBase
from email import encoders

msg = MIMEMultipart()
msg['From'] = os.environ['MAIL_FROM']
msg['To'] = os.environ['MAIL_TO']
msg['Subject'] = os.environ['MAIL_SUBJECT']
msg.attach(MIMEText('Automated mail E2E attachment test', 'plain'))

attach_path = os.environ['MAIL_ATTACH']
with open(attach_path, 'rb') as f:
    part = MIMEBase('application', 'octet-stream')
    part.set_payload(f.read())
encoders.encode_base64(part)
part.add_header('Content-Disposition', f'attachment; filename="{os.path.basename(attach_path)}"')
msg.attach(part)

smtp = smtplib.SMTP(os.environ.get('MAIL_SMTP_HOST', 'localhost'), int(os.environ.get('MAIL_SMTP_PORT', '3025')), timeout=10)
smtp.send_message(msg)
smtp.quit()
PY

sleep 2

curl -s -f -X POST "${API_URL}/api/v1/integration/mail/fetch" \
  -H "Authorization: Bearer ${TOKEN}" >/dev/null

DOC_ID=""
for _ in {1..12}; do
  DOC_ID=$(curl -s -H "Authorization: Bearer ${TOKEN}" "${API_URL}/api/v1/folders/${FOLDER_ID}/contents" \
    | jq -r --arg name "${ATTACH_NAME}" '(.content // .contents // [])[]? | select(.name==$name) | .id' | head -n 1)
  if [[ -n "${DOC_ID}" && "${DOC_ID}" != "null" ]]; then
    break
  fi
  sleep 2
done

if [[ -z "${DOC_ID}" || "${DOC_ID}" == "null" ]]; then
  echo "Attachment document not found in folder" >&2
  exit 1
fi

TAG_HIT=$(curl -s -H "Authorization: Bearer ${TOKEN}" "${API_URL}/api/v1/nodes/${DOC_ID}" \
  | jq -r --arg tag "${TAG_NAME}" '.tags[]? | select(.==$tag)' | head -n 1)

if [[ "${TAG_HIT}" != "${TAG_NAME}" ]]; then
  echo "Tag not applied to ingested document" >&2
  exit 1
fi

echo "MAIL_E2E_TS=${TS}"
echo "MAIL_FOLDER_ID=${FOLDER_ID}"
echo "MAIL_TAG_ID=${TAG_ID}"
echo "MAIL_ACCOUNT_ID=${ACCOUNT_ID}"
echo "MAIL_RULE_ID=${RULE_ID}"
echo "MAIL_DOC_ID=${DOC_ID}"
echo "MAIL_ATTACHMENT_NAME=${ATTACH_NAME}"
