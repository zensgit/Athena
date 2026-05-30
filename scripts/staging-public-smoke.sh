#!/usr/bin/env bash
# Public, no-secret staging smoke for issue #20.
#
# Verifies the current public entrypoint shape:
#   - /health is reachable
#   - OIDC discovery is reachable and returns an issuer
#   - the current hashed frontend main bundle is discoverable from index.html
#   - the current main bundle downloads successfully
#
# This script intentionally uses -k by default because the bare-IP staging host currently uses a
# self-signed cert. Pass a future trusted hostname after owner completes #20 Path B/C; at that point
# add --trusted to require normal TLS validation.
#
# Usage:
#   scripts/staging-public-smoke.sh
#   scripts/staging-public-smoke.sh https://staging.example.com --trusted
set -euo pipefail

BASE_URL="${1:-https://23.254.236.11}"
TLS_MODE="${2:---insecure}"

case "$TLS_MODE" in
  --insecure) CURL_TLS=(-k) ;;
  --trusted) CURL_TLS=() ;;
  *)
    echo "Usage: $0 [https://host] [--insecure|--trusted]" >&2
    exit 2
    ;;
esac

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

trim_trailing_slash() {
  printf '%s' "${1%/}"
}

BASE_URL="$(trim_trailing_slash "$BASE_URL")"
tmpdir="$(mktemp -d)"
trap 'rm -rf "$tmpdir"' EXIT

echo "base_url=$BASE_URL"
echo "tls_mode=${TLS_MODE#--}"

health_code="$(
  curl "${CURL_TLS[@]}" -sS -o "$tmpdir/health.out" -w '%{http_code}' "$BASE_URL/health"
)"
[ "$health_code" = "200" ] || fail "/health expected 200, got $health_code"
echo "health_http=$health_code"

oidc_code="$(
  curl "${CURL_TLS[@]}" -sS -o "$tmpdir/oidc.json" -w '%{http_code}' \
    "$BASE_URL/realms/ecm/.well-known/openid-configuration"
)"
[ "$oidc_code" = "200" ] || fail "OIDC discovery expected 200, got $oidc_code"
issuer="$(
  python3 - "$tmpdir/oidc.json" <<'PY'
import json, sys
with open(sys.argv[1]) as f:
    print(json.load(f).get("issuer", ""))
PY
)"
[ -n "$issuer" ] || fail "OIDC discovery did not include issuer"
echo "oidc_http=$oidc_code"
echo "oidc_issuer=$issuer"

curl "${CURL_TLS[@]}" -sS -o "$tmpdir/index.html" "$BASE_URL/"
js_path="$(
  grep -Eo '/static/js/main\.[^"]+\.js' "$tmpdir/index.html" | head -1 || true
)"
[ -n "$js_path" ] || fail "could not resolve current main JS path from index.html"
echo "js_path=$js_path"

js_metrics="$(
  curl "${CURL_TLS[@]}" -sS -H 'Accept-Encoding: gzip' -o /dev/null \
    -w 'http=%{http_code} time=%{time_total}s size=%{size_download} speed=%{speed_download}Bps' \
    "$BASE_URL$js_path"
)"
echo "js_gzip_$js_metrics"
case "$js_metrics" in
  http=200\ *) ;;
  *) fail "main JS download did not return 200" ;;
esac

echo "result=pass"
