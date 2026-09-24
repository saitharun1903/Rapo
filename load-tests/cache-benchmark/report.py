"""Turns the two k6 summaries and the backend's cache counters into a Markdown comparison table.

Usage: python report.py <results-dir>
Reads summary-uncached.json, summary-cached.json, and cache-counters-cached.txt (Prometheus text scraped
from the backend right after the cached run). Prints Markdown to stdout.
"""
import json
import pathlib
import re
import sys

SCENARIOS = [("estimates", "POST /api/fares/estimate"), ("search", "GET /api/geo/search")]
STATS = [("med", "p50"), ("p(95)", "p95"), ("p(99)", "p99")]


def metric(summary, name):
    metrics = summary.get("metrics", {})
    entry = metrics.get(name)
    if entry is None:
        raise KeyError(f"metric {name} missing from k6 summary; available: {sorted(metrics)}")
    return entry.get("values", entry)


def row(summary, scenario, duration_seconds):
    latency = metric(summary, f"http_req_duration{{scenario:{scenario}}}")
    requests = metric(summary, f"http_reqs{{scenario:{scenario}}}")["count"]
    failed = metric(summary, f"http_req_failed{{scenario:{scenario}}}")["rate"]
    return {
        "rps": requests / duration_seconds,
        "requests": requests,
        "failed": failed,
        **{label: latency[key] for key, label in STATS},
    }


def cache_counts(prometheus_text):
    counts = {}
    pattern = re.compile(r'^rideflow_cache_requests_total\{(?P<labels>[^}]*)\} (?P<value>[0-9.eE+-]+)$')
    for line in prometheus_text.splitlines():
        match = pattern.match(line)
        if not match:
            continue
        labels = dict(part.split("=", 1) for part in match.group("labels").split(","))
        cache = labels["cache"].strip('"')
        result = labels["result"].strip('"')
        counts.setdefault(cache, {})[result] = float(match.group("value"))
    return counts


def main():
    results = pathlib.Path(sys.argv[1])
    duration_seconds = float(sys.argv[2]) if len(sys.argv) > 2 else 60.0
    uncached = json.loads((results / "summary-uncached.json").read_text())
    cached = json.loads((results / "summary-cached.json").read_text())

    print("| Endpoint | Cache | Requests/s | p50 (ms) | p95 (ms) | p99 (ms) | Error rate |")
    print("|---|---|---|---|---|---|---|")
    for scenario, label in SCENARIOS:
        for mode, summary in (("off", uncached), ("on", cached)):
            r = row(summary, scenario, duration_seconds)
            print(f"| `{label}` | {mode} | {r['rps']:.1f} | {r['p50']:.1f} | {r['p95']:.1f} | {r['p99']:.1f} "
                  f"| {r['failed'] * 100:.2f}% |")

    counters = results / "cache-counters-cached.txt"
    if counters.exists():
        print()
        print("Cache outcomes during the cached run (from `rideflow_cache_requests_total`):")
        print()
        print("| Cache | Hits | Misses | Hit rate |")
        print("|---|---|---|---|")
        for cache, outcome in sorted(cache_counts(counters.read_text()).items()):
            hits, misses = outcome.get("hit", 0.0), outcome.get("miss", 0.0)
            if hits + misses:
                print(f"| {cache} | {hits:.0f} | {misses:.0f} | {hits / (hits + misses) * 100:.1f}% |")


if __name__ == "__main__":
    main()
