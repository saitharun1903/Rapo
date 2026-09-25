import { Badge, type Tone } from "@/components/ui/surface";
import type { RideStatus } from "@/lib/api/types";
import { humanize } from "@/lib/format";

const TONES: Record<RideStatus, Tone> = {
  REQUESTED: "neutral",
  MATCHING: "warning",
  DRIVER_ASSIGNED: "brand",
  DRIVER_ARRIVING: "brand",
  DRIVER_ARRIVED: "brand",
  IN_PROGRESS: "brand",
  COMPLETED: "success",
  CANCELLED: "danger",
  EXPIRED: "neutral",
};

export function StatusBadge({ status }: { status: RideStatus }) {
  return <Badge tone={TONES[status]}>{humanize(status)}</Badge>;
}
