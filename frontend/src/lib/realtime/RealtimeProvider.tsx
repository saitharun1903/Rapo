"use client";

import { Client, type StompSubscription } from "@stomp/stompjs";
import { useQueryClient } from "@tanstack/react-query";
import { createContext, useCallback, useContext, useEffect, useEffectEvent, useMemo, useRef, useState } from "react";
import { refreshSession } from "@/lib/api/client";
import { session } from "@/lib/auth/session";
import { config } from "@/lib/config";
import { reconnectDelay } from "./backoff";
import type { ConnectionState, LocationReport, Payloads, SubscribableDestination } from "./types";
import { Destinations } from "./types";

/** Matches the server's heartbeat (docs/events.md §2.1). */
const HEARTBEAT_MS = 10_000;
/** stompjs schedules its own retry after this; the real, jittered backoff runs in beforeConnect. */
const STOMP_RETRY_DELAY_MS = 1;
/** Refresh before connecting if the token would expire within this (§2.5). */
const TOKEN_MARGIN_MS = 60_000;
/** The server closes a socket whose access token expired with this code (§2.1). */
const TOKEN_EXPIRED_CLOSE_CODE = 4001;
const AUTH_ERROR_CODES = new Set(["UNAUTHENTICATED", "INVALID_TOKEN"]);

type Handler = (payload: unknown) => void;

type Realtime = {
  state: ConnectionState;
  subscribe: <D extends SubscribableDestination>(destination: D, handler: (payload: Payloads[D]) => void) => () => void;
  /** Sends a driver location report; false when not connected (the caller keeps the report for next time). */
  sendLocation: (report: LocationReport) => boolean;
};

const RealtimeContext = createContext<Realtime | null>(null);

/** Queries tagged with this meta are refetched after a reconnect, repairing any pushes missed meanwhile. */
export const REALTIME_SNAPSHOT = { realtime: true } as const;

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * One STOMP connection per signed-in tab, following the protocol in docs/events.md §2.5: backoff with full
 * jitter, a fresh token before each connect, re-subscription after it, then snapshot refetches.
 */
export function RealtimeProvider({ children }: { children: React.ReactNode }) {
  const queryClient = useQueryClient();
  const [state, setState] = useState<ConnectionState>("idle");
  const clientRef = useRef<Client | null>(null);
  const handlers = useRef(new Map<string, Set<Handler>>());
  const subscriptions = useRef(new Map<string, StompSubscription>());

  const listen = useCallback((client: Client, destination: string) => {
    subscriptions.current.set(destination, client.subscribe(destination, (message) => {
      let payload: unknown;
      try {
        payload = JSON.parse(message.body);
      } catch (error) {
        console.error(`Unreadable message on ${destination}`, error);
        return;
      }
      handlers.current.get(destination)?.forEach((handler) => handler(payload));
    }));
  }, []);

  useEffect(() => {
    const active = subscriptions.current;
    let attempt = 0;
    let tokenRejected = false;
    let connectedBefore = false;
    const client = new Client({
      brokerURL: config.wsUrl,
      reconnectDelay: STOMP_RETRY_DELAY_MS,
      heartbeatIncoming: HEARTBEAT_MS,
      heartbeatOutgoing: HEARTBEAT_MS,
    });

    client.beforeConnect = async () => {
      setState(attempt === 0 ? "connecting" : "reconnecting");
      if (attempt > 0) {
        await sleep(reconnectDelay(attempt));
      }
      attempt += 1;
      if (tokenRejected || session.expiresWithin(TOKEN_MARGIN_MS)) {
        tokenRejected = false;
        await refreshSession();
      }
      const token = session.accessToken();
      if (token === null) {
        // Signed out meanwhile: stop instead of retrying without credentials.
        await client.deactivate();
        setState("idle");
        return;
      }
      client.connectHeaders = { Authorization: `Bearer ${token}` };
    };

    client.onConnect = () => {
      attempt = 0;
      setState("connected");
      active.clear();
      handlers.current.forEach((_, destination) => listen(client, destination));
      if (connectedBefore) {
        void queryClient.invalidateQueries({ predicate: (query) => query.meta?.realtime === true });
      }
      connectedBefore = true;
    };

    client.onWebSocketClose = (event) => {
      if (event.code === TOKEN_EXPIRED_CLOSE_CODE) {
        tokenRejected = true;
      }
      if (client.active) {
        setState("reconnecting");
      }
    };

    client.onStompError = (frame) => {
      if (AUTH_ERROR_CODES.has(frame.headers.message ?? "")) {
        tokenRejected = true;
      }
      console.warn("STOMP error frame", frame.headers.message);
    };

    clientRef.current = client;
    client.activate();
    return () => {
      clientRef.current = null;
      active.clear();
      void client.deactivate();
    };
  }, [listen, queryClient]);

  const subscribe = useCallback(<D extends SubscribableDestination>(
    destination: D, handler: (payload: Payloads[D]) => void,
  ) => {
    const forDestination = handlers.current.get(destination) ?? new Set<Handler>();
    const wrapped = handler as Handler;
    forDestination.add(wrapped);
    handlers.current.set(destination, forDestination);
    const client = clientRef.current;
    if (client?.connected && !subscriptions.current.has(destination)) {
      listen(client, destination);
    }
    return () => {
      forDestination.delete(wrapped);
      if (forDestination.size === 0) {
        handlers.current.delete(destination);
        subscriptions.current.get(destination)?.unsubscribe();
        subscriptions.current.delete(destination);
      }
    };
  }, [listen]);

  const sendLocation = useCallback((report: LocationReport) => {
    const client = clientRef.current;
    if (!client?.connected) {
      return false;
    }
    client.publish({ destination: Destinations.driverLocation, body: JSON.stringify(report) });
    return true;
  }, []);

  const value = useMemo(() => ({ state, subscribe, sendLocation }), [state, subscribe, sendLocation]);
  return <RealtimeContext.Provider value={value}>{children}</RealtimeContext.Provider>;
}

export function useRealtime(): Realtime {
  const realtime = useContext(RealtimeContext);
  if (realtime === null) {
    throw new Error("useRealtime must be used inside RealtimeProvider");
  }
  return realtime;
}

/** Calls `handler` for every message on `destination` while the component is mounted. */
export function useRealtimeSubscription<D extends SubscribableDestination>(
  destination: D, handler: (payload: Payloads[D]) => void, enabled = true,
) {
  const { subscribe } = useRealtime();
  const onMessage = useEffectEvent(handler);
  useEffect(() => {
    if (!enabled) {
      return undefined;
    }
    return subscribe(destination, (payload) => onMessage(payload));
  }, [destination, enabled, subscribe]);
}
