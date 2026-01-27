#!/usr/bin/env bash
set -euo pipefail

# Run the scheduled rules E2E multiple times to catch flaky races.
RUNS="${1:-3}"
UI_URL="${ECM_UI_URL:-http://localhost:3000}"
API_URL="${ECM_API_URL:-http://localhost:7700}"

export ECM_UI_URL="$UI_URL"
export ECM_API_URL="$API_URL"

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

for i in $(seq 1 "$RUNS"); do
  echo "[scheduled-rules] run $i/$RUNS (UI=$ECM_UI_URL API=$ECM_API_URL)"
  npx playwright test e2e/ui-smoke.spec.ts -g "Scheduled Rules"
done

