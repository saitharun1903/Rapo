"""Summarises a JaCoCo CSV report per package.

Writes a Markdown table (for $GITHUB_STEP_SUMMARY) to stdout. With --annotate it writes workflow notices
instead: the totals, and the line coverage of every package, so they are visible on the run page (and
through the checks API) without downloading the report.

Usage: coverage_summary.py <jacoco.csv> [--annotate]
"""

import csv
import sys
from collections import defaultdict

SERVICE_PREFIX = "com.rideflow.service"
COUNTERS = ("LINE", "BRANCH")


def percent(covered: int, missed: int) -> str:
    total = covered + missed
    return "n/a" if total == 0 else f"{100 * covered / total:.1f}%"


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__, file=sys.stderr)
        return 2
    annotate = "--annotate" in sys.argv[2:]

    per_package: dict[str, dict[str, list[int]]] = defaultdict(lambda: {name: [0, 0] for name in COUNTERS})
    with open(sys.argv[1], newline="", encoding="utf-8") as report:
        for row in csv.DictReader(report):
            for name in COUNTERS:
                counts = per_package[row["PACKAGE"]][name]
                counts[0] += int(row[f"{name}_COVERED"])
                counts[1] += int(row[f"{name}_MISSED"])

    def total(packages: list[str]) -> dict[str, list[int]]:
        sums = {name: [0, 0] for name in COUNTERS}
        for package in packages:
            for name in COUNTERS:
                sums[name][0] += per_package[package][name][0]
                sums[name][1] += per_package[package][name][1]
        return sums

    everything = total(list(per_package))
    services = total([package for package in per_package if package.startswith(SERVICE_PREFIX)])

    if annotate:
        print(f"::notice title=Coverage::All: lines {percent(*everything['LINE'])}, branches {percent(*everything['BRANCH'])}. "
              f"Service layer: lines {percent(*services['LINE'])}, branches {percent(*services['BRANCH'])}.")
        # Workflow commands are one line; '%0A' is an encoded newline.
        lines = "%0A".join(f"{package}: lines {percent(*per_package[package]['LINE'])}, "
                           f"branches {percent(*per_package[package]['BRANCH'])}" for package in sorted(per_package))
        print(f"::notice title=Coverage by package::{lines}")
        return 0

    print("## Backend coverage (unit + integration tests)\n")
    print("| Package | Lines | Branches |")
    print("|---|---|---|")
    for package in sorted(per_package):
        counts = per_package[package]
        print(f"| `{package}` | {percent(*counts['LINE'])} | {percent(*counts['BRANCH'])} |")
    print(f"| **All** | **{percent(*everything['LINE'])}** | **{percent(*everything['BRANCH'])}** |")
    print(f"| **Service layer** | **{percent(*services['LINE'])}** | **{percent(*services['BRANCH'])}** |")
    return 0


if __name__ == "__main__":
    sys.exit(main())
