#!/usr/bin/env bash
# Runs every scenario at each load level, one k6 run at a time, against a backend on BASE_URL (default
# http://localhost:8080). Scenarios never overlap: each creates its own accounts, and drivers left online by
# one run cannot take another's offers. Summaries land in load-tests/results/scenarios/.
#
# Needs Docker (k6 runs in its container) and ADMIN_EMAIL / ADMIN_PASSWORD for an admin account, which
# verifies the test drivers.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
RESULTS="$ROOT/load-tests/results/scenarios"
K6_IMAGE=grafana/k6:2.3.0
BASE_URL=${BASE_URL:-http://localhost:8080}
DURATION=${DURATION:-60s}
LEVELS=${LEVELS:-"10 50 100"}
SCENARIOS=${SCENARIOS:-"login nearby booking lifecycle"}
: "${ADMIN_EMAIL:?Set ADMIN_EMAIL}" "${ADMIN_PASSWORD:?Set ADMIN_PASSWORD}"

mkdir -p "$RESULTS"
# The k6 container runs as a non-root user and writes its summary here.
chmod 777 "$RESULTS"

for scenario in $SCENARIOS; do
  # Lifecycle drivers of earlier levels stay on their grid cells until the presence sweeper takes them
  # offline, so each level starts on fresh cells.
  cell_offset=0
  for vus in $LEVELS; do
    echo "=== $scenario at $vus VUs"
    docker run --rm --network host -v "$ROOT/load-tests:/work" -w /work \
      -e BASE_URL="$BASE_URL" -e VUS="$vus" -e DURATION="$DURATION" -e RUN_ID="$(date +%s%N | cut -c1-13)" \
      -e CELL_OFFSET="$cell_offset" -e RESULTS_DIR=results/scenarios \
      -e ADMIN_EMAIL -e ADMIN_PASSWORD \
      "$K6_IMAGE" run --quiet "scenarios/$scenario.js"
    cell_offset=$((cell_offset + vus))
  done
done
