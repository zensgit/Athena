#!/usr/bin/env bash
set -euo pipefail

TARGET_URL="${1:-${ECM_UI_URL:-http://localhost:3000}}"
ALLOW_STATIC="${ALLOW_STATIC:-0}"

if ! command -v curl >/dev/null 2>&1; then
  echo "error: curl is required"
  exit 1
fi

if ! html="$(curl -fsS --max-time 10 "${TARGET_URL}")"; then
  echo "error: failed to fetch target url: ${TARGET_URL}"
  exit 1
fi

detected_mode="unknown"
if echo "${html}" | grep -q 'static/js/bundle.js'; then
  detected_mode="dev"
elif echo "${html}" | grep -Eq 'static/js/main\.[a-f0-9]{8,}\.js'; then
  detected_mode="static"
fi

echo "target_url=${TARGET_URL}"
echo "detected_mode=${detected_mode}"

if [[ "${detected_mode}" == "dev" ]]; then
  echo "ok: detected development bundle target"
  exit 0
fi

if [[ "${detected_mode}" == "static" ]]; then
  echo "warning: detected static/prebuilt bundle target"
  echo "warning: static target can be stale against current branch code"
  if [[ "${ALLOW_STATIC}" == "1" ]]; then
    echo "ok: static target allowed by ALLOW_STATIC=1"
    exit 0
  fi
  echo "hint: use ECM_UI_URL=http://localhost:3000 for branch-accurate E2E"
  exit 2
fi

echo "warning: unable to classify target build mode"
echo "hint: check ${TARGET_URL} manually before E2E"
exit 3
