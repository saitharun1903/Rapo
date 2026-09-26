import { BadgeCheck, Star } from "lucide-react";
import type { RideResponse } from "@/lib/api/types";
import { formatRating, humanize } from "@/lib/format";

type Driver = NonNullable<RideResponse["driver"]>;

export function initialsOf(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  const first = parts[0]?.[0] ?? "";
  const last = parts.length > 1 ? parts[parts.length - 1][0] : "";
  return (first + last).toUpperCase() || "?";
}

/** A number plate as it looks on the car, so the rider can match it at a glance. */
export function Plate({ number, size = "md" }: { number: string; size?: "md" | "lg" }) {
  return (
    <span className={size === "lg"
      ? "inline-flex items-center rounded-[6px] border-2 border-ink bg-white px-2.5 py-1 font-mono text-lg font-semibold tracking-[0.12em] text-[#121311]"
      : "inline-flex items-center rounded-[5px] border-[1.5px] border-ink bg-white px-2 py-0.5 font-mono text-sm font-semibold tracking-[0.1em] text-[#121311]"}>
      {number}
    </span>
  );
}

/**
 * Who is coming and in what. Raido stores no driver photos, so a monogram stands in; "Verified" means an admin
 * approved this driver's licence and vehicle, which every driver must have before going online.
 */
export function DriverCard({ driver, plateSize = "md" }: { driver: Driver; plateSize?: "md" | "lg" }) {
  const vehicle = driver.vehicle;
  return (
    <div className="flex items-center gap-3.5">
      <div aria-hidden className="flex size-12 shrink-0 items-center justify-center rounded-control bg-ink text-sm font-semibold tracking-wide text-ink-fg">
        {initialsOf(driver.fullName)}
      </div>
      <div className="min-w-0 flex-1">
        <p className="flex items-center gap-1.5 font-medium text-fg">
          <span className="truncate">{driver.fullName}</span>
          <BadgeCheck className="size-4 shrink-0 text-success" aria-label="Verified driver" />
        </p>
        <p className="flex items-center gap-1 whitespace-nowrap text-xs text-fg-muted">
          <Star className="size-3.5 fill-current text-fg" aria-hidden />
          <span className="num text-fg">{formatRating(driver.ratingAvg)}</span>
          {driver.ratingCount > 0 && <span className="num">· {driver.ratingCount} ratings</span>}
        </p>
        {vehicle && (
          <p className="truncate text-xs text-fg-muted">{vehicle.color} {vehicle.make} {vehicle.model} · {humanize(vehicle.category)}</p>
        )}
      </div>
      {vehicle && <Plate number={vehicle.plateNumber} size={plateSize} />}
    </div>
  );
}
