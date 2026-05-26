#!/usr/bin/env bash
# P0a-3b / matrix A11 — static regression guard for ml-service non-root hardening.
#
# This is a STATIC text check only. It proves the Dockerfile still declares a non-root user and
# chowns the writable paths — it CANNOT prove runtime correctness (that a non-root process can
# actually boot, serve /health, and write the model volume). That runtime guarantee requires a
# Docker daemon (docker build/run + /health + /train) and is gate item B4 (owner-signed, off-box).
# CI does not build/start ml-service, so this script is the only on-box guard against accidentally
# regressing the USER directive.
#
# Usage:  scripts/ml-service-dockerfile-check.sh   (run from repo root; exits non-zero on failure)
set -euo pipefail

DOCKERFILE="${1:-ml-service/Dockerfile}"

fail() { echo "FAIL: $1" >&2; exit 1; }

[ -f "$DOCKERFILE" ] || fail "Dockerfile not found: $DOCKERFILE"

# 1. A non-root USER must be declared with the fixed numeric uid 10001.
grep -Eq '^USER[[:space:]]+10001(:10001)?[[:space:]]*$' "$DOCKERFILE" \
    || fail "missing 'USER 10001:10001' (non-root) directive"

# 2. The writable paths must be chowned to 10001 before privilege drop.
grep -Eq 'chown -R 10001:10001 /var/ml-service /app' "$DOCKERFILE" \
    || fail "missing 'chown -R 10001:10001 /var/ml-service /app'"

# 3. The user must exist (groupadd/useradd with the fixed uid).
grep -q 'useradd -r -g app -u 10001 app' "$DOCKERFILE" \
    || fail "missing 'useradd -r -g app -u 10001 app'"

# 4. Privilege must not be reset back to root after the drop.
#    (find the USER line numbers; if any USER 0/root appears AFTER 'USER 10001', that's a regression)
nonroot_line=$(grep -nE '^USER[[:space:]]+10001' "$DOCKERFILE" | head -1 | cut -d: -f1)
if grep -nE '^USER[[:space:]]+(0|root)([[:space:]]|$)' "$DOCKERFILE" \
        | awk -F: -v n="$nonroot_line" '$1 > n {found=1} END {exit !found}'; then
    fail "privilege is reset to root (USER 0/root) AFTER the non-root drop"
fi

echo "OK: ml-service Dockerfile declares non-root (uid 10001) and chowns writable paths."
echo "    NOTE: runtime correctness is NOT proven here — see B4 in"
echo "    docs/HARDENING_P0A3B_MLSERVICE_NONROOT_BRIEF_20260526.md"
