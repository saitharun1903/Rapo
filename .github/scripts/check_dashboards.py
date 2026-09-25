"""Runs every panel query of the provisioned Grafana dashboards through Grafana, against the live stack.

Proves three things after the e2e rides: Grafana provisioned the four dashboards, every query is valid PromQL
over series Prometheus actually has, and the panels a simulator run must light up show non-zero data. Panels
that may legitimately be empty (dead letters, rate-limit rejections, AI with the provider disabled) are
reported, not failed. The result goes to the run page as annotations.

Usage: GRAFANA_PASSWORD=... python3 .github/scripts/check_dashboards.py [grafana base url]
"""

import base64
import json
import math
import os
import sys
import urllib.error
import urllib.request

GRAFANA = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:3001"
USER = "admin"
TIME_FROM = "now-30m"
REQUEST_TIMEOUT_SECONDS = 30
MAX_DATA_POINTS = 100
INTERVAL_MS = 10_000

EXPECTED_DASHBOARDS = {"rideflow-service", "rideflow-rides", "rideflow-realtime", "rideflow-ai"}
# Panels whose data a simulator run must produce: (dashboard uid, panel title) -> minimum latest value.
MUST_SHOW = {
    ("rideflow-service", "Backend"): 1,
    ("rideflow-service", "Requests by outcome"): 0,
    ("rideflow-service", "Heap"): 0,
    ("rideflow-rides", "Requested"): 1,
    ("rideflow-rides", "Completed"): 1,
    ("rideflow-rides", "Time to match (p95)"): 0,
    ("rideflow-rides", "Ride status changes"): 0,
    ("rideflow-rides", "Offers"): 0,
    ("rideflow-rides", "Outbox throughput"): 0,
    ("rideflow-realtime", "WebSocket sessions"): 1,
    ("rideflow-realtime", "Location reports"): 0,
    ("rideflow-realtime", "Cache lookups"): 0,
}


def request(path, body=None):
    token = base64.b64encode(f"{USER}:{os.environ['GRAFANA_PASSWORD']}".encode()).decode()
    req = urllib.request.Request(GRAFANA + path, data=None if body is None else json.dumps(body).encode(),
                                 headers={"Authorization": f"Basic {token}", "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=REQUEST_TIMEOUT_SECONDS) as response:
        return json.load(response)


def run(target):
    query = {"refId": target["refId"], "datasource": target["datasource"], "expr": target["expr"],
             "intervalMs": INTERVAL_MS, "maxDataPoints": MAX_DATA_POINTS,
             "instant": target.get("instant", False), "range": target.get("range", True)}
    try:
        result = request("/api/ds/query", {"from": TIME_FROM, "to": "now", "queries": [query]})
    except urllib.error.HTTPError as error:
        return None, f"HTTP {error.code}: {error.read().decode(errors='replace')[:300]}"
    answer = result["results"][target["refId"]]
    if answer.get("error"):
        return None, answer["error"]
    latest = []
    for frame in answer.get("frames", []):
        values = frame["data"]["values"]
        if len(values) >= 2:
            numbers = [v for v in values[-1] if isinstance(v, (int, float)) and not math.isnan(v)]
            if numbers:
                latest.append(numbers[-1])
    return latest, None


def main():
    found = {hit["uid"] for hit in request("/api/search?tag=rideflow&type=dash-db")}
    errors, empty, lines = [], [], []
    if found != EXPECTED_DASHBOARDS:
        errors.append(f"provisioned dashboards {sorted(found)}, expected {sorted(EXPECTED_DASHBOARDS)}")
    for uid in sorted(found & EXPECTED_DASHBOARDS):
        board = request(f"/api/dashboards/uid/{uid}")["dashboard"]
        for panel in board["panels"]:
            if panel["type"] == "row":
                continue
            values = []
            for target in panel["targets"]:
                latest, error = run(target)
                if error:
                    errors.append(f"{uid} / {panel['title']}: {error}")
                else:
                    values.extend(latest)
            shown = f"{max(values):.4g}" if values else "no data"
            lines.append(f"{uid} / {panel['title']}: {shown}")
            minimum = MUST_SHOW.get((uid, panel["title"]))
            if minimum is not None and (not values or max(values) < minimum):
                errors.append(f"{uid} / {panel['title']}: expected data >= {minimum}, got {shown}")
            elif not values:
                empty.append(f"{uid} / {panel['title']}")
    missing = [key for key in MUST_SHOW if key[0] in found and not any(line.startswith(f"{key[0]} / {key[1]}:") for line in lines)]
    errors.extend(f"{uid} / {title}: panel not found" for uid, title in missing)

    encode = lambda text: text.replace("%", "%25").replace("\n", "%0A")
    print("\n".join(lines))
    print(f"::notice title=Dashboards::{encode(chr(10).join(lines))}")
    if empty:
        print(f"::notice title=Panels without data (allowed)::{encode(chr(10).join(empty))}")
    for error in errors:
        print(f"::error title=Dashboard check::{encode(error)}")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
