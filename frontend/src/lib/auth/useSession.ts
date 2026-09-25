"use client";

import { useSyncExternalStore } from "react";
import type { Role } from "@/lib/api/types";
import { session, type Session } from "./session";

const SERVER_SNAPSHOT: Session = { status: "loading" };

export function useSession(): Session {
  return useSyncExternalStore(session.subscribe, session.get, () => SERVER_SNAPSHOT);
}

const HOME_BY_ROLE: Record<Role, string> = {
  PASSENGER: "/ride",
  DRIVER: "/drive",
  ADMIN: "/admin",
};

export function homeFor(role: Role): string {
  return HOME_BY_ROLE[role];
}
