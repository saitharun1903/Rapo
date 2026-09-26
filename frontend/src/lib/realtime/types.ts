import type { Eta, GeoPoint, NotificationItem, RideMessage, RideOffer, RideResponse, RideStatus } from "@/lib/api/types";

/**
 * STOMP destinations and payloads (docs/events.md §2). They are not REST endpoints, so they are not in
 * docs/openapi.json; where a payload is a REST type, the generated type is reused.
 */
export const Destinations = {
  rides: "/user/queue/rides",
  rideLocation: "/user/queue/ride-location",
  rideOffers: "/user/queue/ride-offers",
  presence: "/user/queue/presence",
  notifications: "/user/queue/notifications",
  errors: "/user/queue/errors",
  rideMessages: "/user/queue/ride-messages",
  adminActivity: "/topic/admin/activity",
  driverLocation: "/app/drivers/location",
} as const;

export type SubscribableDestination = Exclude<(typeof Destinations)[keyof typeof Destinations], "/app/drivers/location">;

export type RideLocationMessage = {
  rideId: string;
  location: GeoPoint;
  headingDeg: number | null;
  speedMps: number | null;
  recordedAt: string;
  eta: Eta | null;
};

export type RideOfferMessage =
  | { type: "OFFER"; rideId: string; offer: RideOffer }
  | { type: "WITHDRAWN"; rideId: string; offer: null };

export type PresenceMessage = {
  availability: "OFFLINE";
  reason: "LOCATION_TIMEOUT" | "ACCOUNT_SUSPENDED";
  occurredAt: string;
};

export type StompErrorMessage = { code: string; message: string; destination: string };

export type AdminActivityMessage = {
  rideId: string;
  previousStatus: RideStatus | null;
  status: RideStatus;
  actor: "PASSENGER" | "DRIVER" | "SYSTEM" | "ADMIN";
  rideVersion: number;
  occurredAt: string;
};

export type Payloads = {
  "/user/queue/rides": RideResponse;
  "/user/queue/ride-location": RideLocationMessage;
  "/user/queue/ride-offers": RideOfferMessage;
  "/user/queue/presence": PresenceMessage;
  "/user/queue/notifications": NotificationItem;
  "/user/queue/errors": StompErrorMessage;
  "/user/queue/ride-messages": RideMessage;
  "/topic/admin/activity": AdminActivityMessage;
};

export type LocationReport = {
  location: GeoPoint;
  headingDeg?: number | null;
  speedMps?: number | null;
  accuracyMeters?: number | null;
  recordedAt: string;
};

/** "unavailable": the WebSocket URL cannot work from this page (socketUrl.ts), so no connection is attempted. */
export type ConnectionState = "idle" | "connecting" | "connected" | "reconnecting" | "unavailable";
