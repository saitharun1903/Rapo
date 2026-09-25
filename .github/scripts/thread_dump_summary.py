"""Condenses JVM thread dumps (jcmd <pid> Thread.print) into workflow annotations.

Reports the threads that matter for a stall: every JVM's main thread (a stall can sit in Maven or a container
start rather than in a test), those running RideFlow code and those BLOCKED on a monitor, each with its state
and top frames, so an intermittent hang can be diagnosed from the run page (and the checks
API) without the raw log.

Usage: thread_dump_summary.py <dump file>
"""

import re
import sys

MAX_THREADS = 8
MAX_FRAMES = 14
MAX_MESSAGE_CHARS = 3500
APP_PACKAGE = "com.rideflow"


def encode(text: str) -> str:
    """Workflow commands are one line: '%' and newlines are percent-encoded."""
    return text.replace("%", "%25").replace("\r", "").replace("\n", "%0A")


def threads(dump: str) -> list[tuple[str, str, list[str]]]:
    """(name, state, frames) for every thread in the dump."""
    found = []
    for block in re.split(r"\n\s*\n", dump):
        header = re.search(r'^"([^"]+)"', block, re.MULTILINE)
        if not header:
            continue
        state = re.search(r"java\.lang\.Thread\.State: (\S+)", block)
        frames = [line.strip() for line in block.splitlines() if line.strip().startswith(("at ", "- "))]
        found.append((header.group(1), state.group(1) if state else "UNKNOWN", frames))
    return found


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__, file=sys.stderr)
        return 2
    with open(sys.argv[1], encoding="utf-8", errors="replace") as source:
        dump = source.read()

    all_threads = threads(dump)
    suspects = [thread for thread in all_threads
                if thread[0] == "main" or thread[1] == "BLOCKED"
                or any(APP_PACKAGE in frame for frame in thread[2])]
    print(f"::warning title=Thread dump::{len(all_threads)} threads, {len(suspects)} running RideFlow code "
          f"or blocked (or main threads); the first {min(len(suspects), MAX_THREADS)} follow.")
    for name, state, frames in suspects[:MAX_THREADS]:
        body = f"{name} ({state})\n" + "\n".join(frames[:MAX_FRAMES])
        print(f"::warning title=Thread {encode(name)}::{encode(body[:MAX_MESSAGE_CHARS])}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
