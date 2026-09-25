// Summarises Vitest's coverage/coverage-summary.json: a Markdown table on stdout (for $GITHUB_STEP_SUMMARY),
// or with --annotate a workflow notice readable on the run page and through the checks API.
import { readFileSync } from "node:fs";

const COUNTERS = ["lines", "branches", "functions", "statements"];
const { total } = JSON.parse(readFileSync(new URL("../coverage/coverage-summary.json", import.meta.url), "utf8"));

if (process.argv.includes("--annotate")) {
  const figures = COUNTERS.map((counter) => `${counter} ${total[counter].pct}%`).join(", ");
  console.log(`::notice title=Frontend coverage::${figures}`);
} else {
  console.log(["## Frontend coverage (Vitest)", "", "| Counter | Covered |", "|---|---|",
    ...COUNTERS.map((counter) => `| ${counter} | ${total[counter].pct}% |`)].join("\n"));
}
