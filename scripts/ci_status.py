"""One look at GitHub Actions for a commit: every workflow run it triggered, with its status.

It never waits or polls. A workflow skipped by its path filters (or not triggered by the event) has no run
for the commit and is reported as not triggered, not as unfinished; a queued or stuck run is reported as
it is. Check again later by hand if needed.

Usage:  python scripts/ci_status.py [commit]      (default: HEAD)
Anonymous GitHub API calls are limited to 60 an hour; set GITHUB_TOKEN (never commit it) for more.
"""

import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REQUEST_TIMEOUT_SECONDS = 15
MAX_RUNS = 100


def git(*args: str) -> str:
    return subprocess.run(["git", "-C", str(ROOT), *args], check=True, capture_output=True, text=True).stdout.strip()


def main() -> int:
    sha = git("rev-parse", sys.argv[1] if len(sys.argv) > 1 else "HEAD")
    repo = re.sub(r"\.git$", "", re.sub(r"^.*github\.com[:/]", "", git("remote", "get-url", "origin")))
    workflows = sorted(path.stem for path in (ROOT / ".github" / "workflows").glob("*.y*ml"))

    request = urllib.request.Request(
        f"https://api.github.com/repos/{repo}/actions/runs?head_sha={sha}&per_page={MAX_RUNS}",
        headers={"Accept": "application/vnd.github+json"})
    token = os.environ.get("GITHUB_TOKEN")
    if token:
        request.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(request, timeout=REQUEST_TIMEOUT_SECONDS) as response:
            runs = json.load(response)["workflow_runs"]
    except urllib.error.HTTPError as error:
        print(f"GitHub API answered {error.code}: {json.load(error).get('message', '')}")
        return 1

    print(f"Commit {sha[:7]}: {len(runs)} run(s)")
    for run in sorted(runs, key=lambda r: (r["name"], r["event"])):
        outcome = run["conclusion"] or run["status"]
        print(f"  {run['name']:<16} {run['event']:<13} {outcome:<11} {run['html_url']}")
    triggered = {run["name"] for run in runs}
    for name in workflows:
        if name not in triggered:
            print(f"  {name:<16} not triggered (path filters or event)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
