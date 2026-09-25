import { Client } from "@stomp/stompjs";
import { ApiClient, isCode } from "./api.ts";
import type { Config } from "./config.ts";
import { distanceMeters, PathWalker, randomPointNear, type Point } from "./geo.ts";
import { log, logError, sleep } from "./log.ts";

const MS_PER_SECOND = 1_000;
/** The server's pickup geofence is 150 m; the car parks at the pickup point itself. */
const ARRIVE_ATTEMPTS = 3;
const TERMINAL = new Set(["COMPLETED", "CANCELLED", "EXPIRED"]);

type Place = { point: Point; address: string };
type Ride = { id: string; status: string; version: number; pickup: Place; dropoff: Place };
type Offer = { rideId: string; expiresAt: string };
type OfferMessage = { type: "OFFER" | "WITHDRAWN"; rideId: string; offer: Offer | null };
type Route = { path: Point[] };

class RideAborted extends Error {}

/**
 * One seeded driver: signs in, goes online, streams its position over STOMP (the same destination the web
 * app uses), accepts offers and drives each ride along the routed path to the pickup and then the dropoff.
 */
export class DriverBot {
  private readonly api: ApiClient;
  private readonly config: Config;
  private readonly name: string;
  private readonly stomp: Client;
  private position: Point;
  private heading: number | null = null;
  private speed = 0;
  private busy = false;
  /** Aborted when the current ride ends early, e.g. the passenger cancels. */
  private rideAbort: AbortController | null = null;
  private currentRideId: string | null = null;
  private reporter: ReturnType<typeof setInterval> | null = null;

  constructor(config: Config, email: string) {
    this.config = config;
    this.name = email.split("@")[0];
    this.api = new ApiClient(config.apiBaseUrl, email, config.password);
    this.position = randomPointNear(config.center, config.startRadiusMeters);
    this.stomp = new Client({
      webSocketFactory: () => new WebSocket(config.wsUrl),
      reconnectDelay: 2 * MS_PER_SECOND,
      heartbeatIncoming: 10 * MS_PER_SECOND,
      heartbeatOutgoing: 10 * MS_PER_SECOND,
      beforeConnect: () => {
        this.stomp.connectHeaders = { Authorization: `Bearer ${this.api.accessToken}` };
      },
      onConnect: () => this.onConnect(),
      onStompError: (frame) => log(this.name, `STOMP error ${frame.headers.message ?? ""}`),
    });
  }

  async start(): Promise<void> {
    await this.api.signIn();
    const active = await this.api.call<Ride>("GET", "/api/rides/active");
    if (active === null) {
      await this.api.post("/api/drivers/online", { location: this.position });
      log(this.name, `online at ${this.position.lat.toFixed(5)},${this.position.lng.toFixed(5)}`);
    }
    this.stomp.activate();
    this.reporter = setInterval(() => this.report(), this.config.reportIntervalMs);
    if (active !== null) {
      log(this.name, `resuming ride ${active.id} (${active.status})`);
      void this.drive(active);
    }
  }

  async stop(): Promise<void> {
    if (this.reporter !== null) {
      clearInterval(this.reporter);
    }
    this.rideAbort?.abort(new RideAborted("simulator stopping"));
    await this.stomp.deactivate();
    try {
      await this.api.post("/api/drivers/offline");
      log(this.name, "offline");
    } catch (error) {
      logError(this.name, "could not go offline", error);
    }
  }

  private onConnect(): void {
    this.stomp.subscribe("/user/queue/ride-offers", (frame) => this.onOffer(JSON.parse(frame.body) as OfferMessage));
    this.stomp.subscribe("/user/queue/rides", (frame) => this.onRideUpdate(JSON.parse(frame.body) as Ride));
    this.stomp.subscribe("/user/queue/errors", (frame) => log(this.name, `location rejected: ${frame.body}`));
    // Offers made while disconnected are only in the snapshot.
    this.api.call<Offer[]>("GET", "/api/drivers/me/offers")
      .then((offers) => offers?.forEach((offer) => this.onOffer({ type: "OFFER", rideId: offer.rideId, offer })))
      .catch((error: unknown) => logError(this.name, "could not load open offers", error));
  }

  private onOffer(message: OfferMessage): void {
    if (message.type !== "OFFER" || this.busy) {
      return;
    }
    this.busy = true;
    void this.accept(message.rideId);
  }

  private onRideUpdate(ride: Ride): void {
    if (ride.id === this.currentRideId && TERMINAL.has(ride.status) && ride.status !== "COMPLETED") {
      log(this.name, `ride ${ride.id} ended: ${ride.status}`);
      this.rideAbort?.abort(new RideAborted(ride.status));
    }
  }

  private async accept(rideId: string): Promise<void> {
    try {
      await sleep(this.config.acceptDelayMs);
      const ride = await this.api.post<Ride>(`/api/rides/${rideId}/accept`);
      log(this.name, `accepted ride ${rideId}`);
      await this.drive(ride);
    } catch (error) {
      if (isCode(error, "RIDE_ALREADY_ASSIGNED", "OFFER_EXPIRED", "RIDE_NOT_FOUND", "DRIVER_UNAVAILABLE")) {
        log(this.name, `offer for ${rideId} gone: ${(error as Error).message}`);
      } else {
        logError(this.name, `could not take ride ${rideId}`, error);
      }
      this.busy = false;
    }
  }

  /** Takes a ride from wherever it is now to completion. */
  private async drive(ride: Ride): Promise<void> {
    this.busy = true;
    this.currentRideId = ride.id;
    this.rideAbort = new AbortController();
    const signal = this.rideAbort.signal;
    const path = `/api/rides/${ride.id}`;
    try {
      let status = ride.status;
      if (status === "DRIVER_ASSIGNED") {
        status = (await this.api.post<Ride>(`${path}/en-route`)).status;
      }
      if (status === "DRIVER_ARRIVING") {
        await this.travel(ride.pickup.point, signal);
        status = await this.arrive(path);
      }
      if (status === "DRIVER_ARRIVED") {
        await sleep(this.config.boardingMs, signal);
        status = (await this.api.post<Ride>(`${path}/start`)).status;
      }
      if (status === "IN_PROGRESS") {
        await this.travel(ride.dropoff.point, signal);
        await this.reportNow();
        const done = await this.api.post<Ride>(`${path}/complete`);
        log(this.name, `completed ride ${ride.id}: ${done.status}`);
      }
    } catch (error) {
      if (!(error instanceof RideAborted)) {
        logError(this.name, `ride ${ride.id} stopped`, error);
      }
    } finally {
      this.busy = false;
      this.currentRideId = null;
      this.rideAbort = null;
      this.speed = 0;
    }
  }

  private async arrive(path: string): Promise<string> {
    for (let attempt = 1; ; attempt++) {
      await this.reportNow();
      try {
        return (await this.api.post<Ride>(`${path}/arrive`)).status;
      } catch (error) {
        if (!isCode(error, "NOT_AT_PICKUP", "LOCATION_UNAVAILABLE") || attempt >= ARRIVE_ATTEMPTS) {
          throw error;
        }
        await sleep(this.config.reportIntervalMs);
      }
    }
  }

  /** Follows the routed path to `target`, then the last metres straight to it, reporting on the way. */
  private async travel(target: Point, signal: AbortSignal): Promise<void> {
    const from = this.position;
    const route = await this.api.get<Route>(
      `/api/geo/route?fromLat=${from.lat}&fromLng=${from.lng}&toLat=${target.lat}&toLng=${target.lng}`);
    const walker = new PathWalker([from, ...route.path, target]);
    const step = this.config.speedMps * (this.config.reportIntervalMs / MS_PER_SECOND);
    log(this.name, `driving ${Math.round(distanceMeters(from, target))} m (straight line)`);
    this.speed = this.config.speedMps;
    while (!walker.done) {
      await sleep(this.config.reportIntervalMs, signal);
      const next = walker.advance(step);
      this.position = next.position;
      this.heading = next.headingDeg ?? this.heading;
    }
    this.speed = 0;
  }

  private body() {
    return {
      location: this.position,
      headingDeg: this.heading,
      speedMps: this.speed,
      accuracyMeters: null,
      recordedAt: new Date().toISOString(),
    };
  }

  /** Streams the position over STOMP, or over REST when the socket is down. */
  private report(): void {
    if (this.stomp.connected) {
      this.stomp.publish({ destination: "/app/drivers/location", body: JSON.stringify(this.body()) });
      return;
    }
    this.reportNow().catch((error: unknown) => logError(this.name, "location report failed", error));
  }

  /** A synchronous (REST) report, so the server has the position before the next action checks it. */
  private async reportNow(): Promise<void> {
    await this.api.call("POST", "/api/drivers/location", this.body());
  }
}
