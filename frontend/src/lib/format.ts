import type { Money } from "@/lib/api/types";

const METERS_PER_KM = 1000;
const SECONDS_PER_MINUTE = 60;
const MINUTES_PER_HOUR = 60;
/** Below this, show metres rather than a fraction of a kilometre. */
const SHOW_METERS_BELOW = 1000;
const LOCALE = "en-IN";

/** Money arrives as a decimal string; format it without passing through a float's rounding more than once. */
export function formatMoney(money: Money | null | undefined): string {
  if (!money) {
    return "—";
  }
  return new Intl.NumberFormat(LOCALE, { style: "currency", currency: money.currency })
    .format(Number(money.amount));
}

export function formatDistance(meters: number): string {
  if (meters < SHOW_METERS_BELOW) {
    return `${Math.round(meters)} m`;
  }
  return `${(meters / METERS_PER_KM).toFixed(1)} km`;
}

export function formatDuration(seconds: number): string {
  const minutes = Math.max(1, Math.round(seconds / SECONDS_PER_MINUTE));
  if (minutes < MINUTES_PER_HOUR) {
    return `${minutes} min`;
  }
  const hours = Math.floor(minutes / MINUTES_PER_HOUR);
  const rest = minutes % MINUTES_PER_HOUR;
  return rest === 0 ? `${hours} h` : `${hours} h ${rest} min`;
}

export function formatDateTime(iso: string | null | undefined, timeZone?: string): string {
  if (!iso) {
    return "—";
  }
  return new Intl.DateTimeFormat(LOCALE, { dateStyle: "medium", timeStyle: "short", timeZone })
    .format(new Date(iso));
}

export function formatTime(iso: string | null | undefined, timeZone?: string): string {
  if (!iso) {
    return "—";
  }
  return new Intl.DateTimeFormat(LOCALE, { timeStyle: "short", timeZone }).format(new Date(iso));
}

export function formatPercent(ratio: number | null | undefined): string {
  if (ratio === null || ratio === undefined) {
    return "—";
  }
  return new Intl.NumberFormat(LOCALE, { style: "percent", maximumFractionDigits: 1 }).format(ratio);
}

export function formatRating(rating: number | null | undefined): string {
  return rating === null || rating === undefined ? "New" : rating.toFixed(2);
}

/** "ECONOMY" → "Economy", "DRIVER_ARRIVING" → "Driver arriving". */
export function humanize(constant: string): string {
  const words = constant.toLowerCase().split("_");
  return [words[0].charAt(0).toUpperCase() + words[0].slice(1), ...words.slice(1)].join(" ");
}
