import type { RideResponse, RideStatus } from "@/lib/api/types";

export const TERMINAL_STATUSES: readonly RideStatus[] = ["COMPLETED", "CANCELLED", "EXPIRED"];
export const AWAITING_DRIVER: readonly RideStatus[] = ["REQUESTED", "MATCHING"];
/** A driver is assigned and heading to, or waiting at, the pickup. */
export const DRIVER_EN_ROUTE: readonly RideStatus[] = ["DRIVER_ASSIGNED", "DRIVER_ARRIVING", "DRIVER_ARRIVED"];
/** The backend serves a tracking snapshot only in these states. */
export const TRACKABLE: readonly RideStatus[] = [...DRIVER_EN_ROUTE, "IN_PROGRESS"];
/** The passenger may cancel until the trip starts. */
export const PASSENGER_CANCELLABLE: readonly RideStatus[] = [...AWAITING_DRIVER, ...DRIVER_EN_ROUTE];

export function isTerminal(status: RideStatus): boolean {
  return TERMINAL_STATUSES.includes(status);
}

/**
 * Ride updates can arrive out of order (a push racing a snapshot, or two pushes). Each carries the ride's
 * version, which grows with every status change, so an update is applied only when it is newer.
 */
export function newerRide(current: RideResponse | null | undefined, incoming: RideResponse): RideResponse {
  if (current && current.id === incoming.id && current.version >= incoming.version) {
    return current;
  }
  return incoming;
}

const PASSENGER_HEADLINES: Record<RideStatus, string> = {
  REQUESTED: "Request received",
  MATCHING: "Finding your ride",
  DRIVER_ASSIGNED: "Driver assigned",
  DRIVER_ARRIVING: "Your driver is on the way",
  DRIVER_ARRIVED: "Your driver is here",
  IN_PROGRESS: "On the way",
  COMPLETED: "You've arrived",
  CANCELLED: "Ride cancelled",
  EXPIRED: "No driver accepted in time",
};

export function passengerHeadline(status: RideStatus): string {
  return PASSENGER_HEADLINES[status];
}

export type DriverAction = { path: "en-route" | "arrive" | "start" | "complete"; label: string; hint: string };

const DRIVER_ACTIONS: Partial<Record<RideStatus, DriverAction>> = {
  DRIVER_ASSIGNED: { path: "en-route", label: "Start driving to pickup", hint: "The passenger sees you on the map." },
  DRIVER_ARRIVING: { path: "arrive", label: "I have arrived", hint: "You must be at the pickup point." },
  DRIVER_ARRIVED: { path: "start", label: "Start trip", hint: "Start once the passenger is in the car." },
  IN_PROGRESS: { path: "complete", label: "Complete trip", hint: "The fare is calculated from the route driven." },
};

/** The one action that moves the driver's ride forward from its current state, if any. */
export function nextDriverAction(status: RideStatus): DriverAction | null {
  return DRIVER_ACTIONS[status] ?? null;
}

/** Where the car is heading: the pickup until the trip starts, then the dropoff. */
export function currentTarget(ride: RideResponse): RideResponse["pickup"] {
  return ride.status === "IN_PROGRESS" ? ride.dropoff : ride.pickup;
}
