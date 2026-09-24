#!/usr/bin/env bash
# Measures the real latency of the public OSRM and Nominatim servers from this machine: the median of 5
# requests each, 1.5 s apart (well inside both services' usage policies, with an identifying User-Agent).
# Prints OSRM_DELAY_MS=... and NOMINATIM_DELAY_MS=... for the benchmark's upstream stand-ins, so the
# benchmark never sends load to the public servers but still uses realistic upstream latency.
set -euo pipefail

USER_AGENT="RideFlow/0.1 (+https://github.com/saitharun1903/Rapo) cache-benchmark"
SAMPLES=5
PAUSE_SECONDS=1.5
OSRM_URL="https://router.project-osrm.org/route/v1/driving/78.377200,17.443500;78.473800,17.423900?overview=simplified&geometries=geojson"
NOMINATIM_URL="https://nominatim.openstreetmap.org/search?q=charminar&format=jsonv2&limit=5&countrycodes=in"

median_ms() {
  sort -n | awk '{ v[NR] = $1 } END { printf "%d", v[int((NR + 1) / 2)] * 1000 }'
}

sample() {
  local url=$1
  for _ in $(seq "$SAMPLES"); do
    # -f: fail on HTTP errors instead of timing an error page.
    curl -sf -o /dev/null -A "$USER_AGENT" -w '%{time_total}\n' "$url"
    sleep "$PAUSE_SECONDS"
  done
}

osrm_samples=$(sample "$OSRM_URL")
nominatim_samples=$(sample "$NOMINATIM_URL")
echo "OSRM samples (s): $(echo "$osrm_samples" | tr '\n' ' ')" >&2
echo "Nominatim samples (s): $(echo "$nominatim_samples" | tr '\n' ' ')" >&2
echo "OSRM_DELAY_MS=$(echo "$osrm_samples" | median_ms)"
echo "NOMINATIM_DELAY_MS=$(echo "$nominatim_samples" | median_ms)"
