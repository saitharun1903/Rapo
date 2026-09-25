import type { ReportGranularity } from "@/lib/api/types";

const HOUR_MS = 3_600_000;
const DAY_MS = 24 * HOUR_MS;

export type RangeKey = "24h" | "7d" | "30d";

type Range = { label: string; span: number; granularity: ReportGranularity };

export const RANGES: Record<RangeKey, Range> = {
  "24h": { label: "24 hours", span: DAY_MS, granularity: "HOUR" },
  "7d": { label: "7 days", span: 7 * DAY_MS, granularity: "DAY" },
  "30d": { label: "30 days", span: 30 * DAY_MS, granularity: "DAY" },
};

export const RANGE_OPTIONS = (Object.keys(RANGES) as RangeKey[]).map((value) => ({ value, label: RANGES[value].label }));

/**
 * A reporting window ending at the start of the next hour, so it stays the same (and cached) for a while
 * instead of changing on every render.
 */
export function windowFor(key: RangeKey, now: number = Date.now()): { from: string; to: string; granularity: ReportGranularity } {
  const to = Math.ceil(now / HOUR_MS) * HOUR_MS;
  const { span, granularity } = RANGES[key];
  return { from: new Date(to - span).toISOString(), to: new Date(to).toISOString(), granularity };
}
