#!/usr/bin/env bash
# Runs the k6 cache benchmark twice against the packaged backend: caches off, then on. Expects PostGIS on
# :5432, Redis on :6379 (container "redis"), the upstream stand-ins on :8089, and DB_PASSWORD / JWT_SECRET
# in the environment. Results land in load-tests/results/.
set -euo pipefail

ROOT=$(pwd)
RESULTS="$ROOT/load-tests/results"
VUS=${VUS:-20}
DURATION_SECONDS=${DURATION_SECONDS:-60}
K6_IMAGE=grafana/k6:2.3.0
JAR=$(ls backend/target/rideflow-backend-*.jar | grep -v '\.original$' | head -1)

mkdir -p "$RESULTS"
# The k6 container runs as a non-root user and writes its summary into this directory.
chmod 777 "$RESULTS"

for MODE in uncached cached; do
  CACHE_ENABLED=false
  [ "$MODE" = cached ] && CACHE_ENABLED=true
  docker exec redis redis-cli FLUSHALL > /dev/null

  DATABASE_URL=jdbc:postgresql://localhost:5432/rideflow \
  DATABASE_USERNAME=rideflow \
  DATABASE_PASSWORD="$DB_PASSWORD" \
  JWT_SECRET="$JWT_SECRET" \
  REDIS_HOST=localhost \
  REDIS_PORT=6379 \
  ROUTING_BASE_URL=http://localhost:8089 \
  GEOCODING_BASE_URL=http://localhost:8089 \
  RATE_LIMIT_ENABLED=false \
  CACHE_ENABLED="$CACHE_ENABLED" \
    java -jar "$JAR" > "$RESULTS/backend-$MODE.log" 2>&1 &
  BACKEND_PID=$!

  echo "Waiting for the backend ($MODE)..."
  until curl -sf http://localhost:8081/actuator/health/readiness > /dev/null; do
    kill -0 "$BACKEND_PID" 2> /dev/null || { tail -50 "$RESULTS/backend-$MODE.log"; exit 1; }
    sleep 2
  done

  docker run --rm --network host -v "$ROOT/load-tests:/work" -w /work \
    -e MODE="$MODE" -e VUS="$VUS" -e DURATION="${DURATION_SECONDS}s" -e BASE_URL=http://localhost:8080 \
    "$K6_IMAGE" run --quiet cache-benchmark/cache-benchmark.js

  curl -sf http://localhost:8081/actuator/prometheus | grep '^rideflow_cache_requests_total' \
    > "$RESULTS/cache-counters-$MODE.txt" || true
  kill "$BACKEND_PID"
  wait "$BACKEND_PID" || true
done
