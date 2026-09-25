"""Turns the scenario k6 summaries into Markdown tables: one row per scenario and load level.

Usage: python report.py <results-dir> <duration-seconds>
Reads summary-<scenario>-<vus>.json as written by the scenario scripts. Only the measured scenario ("load")
is counted: setup, teardown and background driver reports are not.
"""
import json
import pathlib
import re
import sys

# The request that names each scenario, then the other steps worth a column of their own.
SCENARIOS = {
    "login": ("Login", ["login"]),
    "nearby": ("Nearby search", ["nearby"]),
    "booking": ("Ride creation", ["book", "estimate", "cancel"]),
    "lifecycle": ("Full ride lifecycle", ["book", "accept", "arrive", "start", "complete"]),
}
FILE = re.compile(r"summary-(?P<scenario>[a-z]+)-(?P<vus>\d+)\.json$")


def values(summary, name):
    entry = summary["metrics"].get(name)
    return None if entry is None else entry.get("values", entry)


def ms(trend, stat):
    return "–" if trend is None else f"{trend[stat]:.0f}"


def main():
    results = pathlib.Path(sys.argv[1])
    duration = float(sys.argv[2])
    runs = {}
    for path in results.glob("summary-*.json"):
        match = FILE.search(path.name)
        if match:
            runs.setdefault(match["scenario"], {})[int(match["vus"])] = json.loads(path.read_text())

    for scenario, (title, requests) in SCENARIOS.items():
        if scenario not in runs:
            continue
        print(f"### {title}")
        print()
        main_request = requests[0]
        print(f"| VUs | Iterations/s | Requests/s | `{main_request}` p50 / p95 / p99 (ms) | Failed requests "
              "| Failed checks |")
        print("|---|---|---|---|---|---|")
        for vus, summary in sorted(runs[scenario].items()):
            iterations = values(summary, "iterations{scenario:load}")["count"]
            requests_count = values(summary, "http_reqs{scenario:load}")["count"]
            failed = values(summary, "http_req_failed{scenario:load}")["rate"]
            checks = values(summary, "checks{scenario:load}")
            trend = values(summary, f"http_req_duration{{name:{main_request}}}")
            print(f"| {vus} | {iterations / duration:.1f} | {requests_count / duration:.1f} "
                  f"| {ms(trend, 'med')} / {ms(trend, 'p(95)')} / {ms(trend, 'p(99)')} "
                  f"| {failed * 100:.2f}% | {(1 - checks['rate']) * 100 if checks else 0:.2f}% |")
        if len(requests) > 1:
            print()
            print("p95 per step (ms):")
            print()
            print("| VUs | " + " | ".join(f"`{r}`" for r in requests) + " |")
            print("|---|" + "---|" * len(requests))
            for vus, summary in sorted(runs[scenario].items()):
                cells = [ms(values(summary, f"http_req_duration{{name:{r}}}"), "p(95)") for r in requests]
                print(f"| {vus} | " + " | ".join(cells) + " |")
        if scenario == "lifecycle":
            print()
            print("| VUs | Rides completed | Offers not received in 30 s | Booking to offer p50 / p95 (ms) "
                  "| Booking to completed p50 / p95 (ms) |")
            print("|---|---|---|---|---|")
            for vus, summary in sorted(runs[scenario].items()):
                completed = values(summary, "rides_completed")
                missed = values(summary, "offers_missed")
                offer = values(summary, "time_to_offer")
                ride = values(summary, "ride_lifecycle")
                print(f"| {vus} | {completed['count'] if completed else 0:.0f} | {missed['count'] if missed else 0:.0f} "
                      f"| {ms(offer, 'med')} / {ms(offer, 'p(95)')} | {ms(ride, 'med')} / {ms(ride, 'p(95)')} |")
        if scenario == "nearby":
            print()
            print("Drivers returned per search (median / p95): " + ", ".join(
                f"{vus} VUs {ms(values(s, 'nearby_drivers_found'), 'med')} / {ms(values(s, 'nearby_drivers_found'), 'p(95)')}"
                for vus, s in sorted(runs[scenario].items())))
        print()


if __name__ == "__main__":
    main()
