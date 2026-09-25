import type { AuthResponse, UserResponse } from "@/lib/api/types";

const SECONDS_TO_MS = 1000;

export type Session =
  | { status: "loading" }
  | { status: "anonymous" }
  | { status: "authenticated"; user: UserResponse; accessToken: string; expiresAt: number };

type Listener = () => void;

/**
 * The signed-in user and their access token, kept in memory only (never localStorage), so a script injected
 * into the page cannot read a stored token. A reload restores the session from the HttpOnly refresh cookie.
 */
export class SessionStore {
  private state: Session = { status: "loading" };
  private readonly listeners = new Set<Listener>();

  constructor(private readonly now: () => number = Date.now) {}

  get = (): Session => this.state;

  subscribe = (listener: Listener): (() => void) => {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  };

  accessToken(): string | null {
    return this.state.status === "authenticated" ? this.state.accessToken : null;
  }

  /** True when there is no token, or it expires within `marginMs`. */
  expiresWithin(marginMs: number): boolean {
    return this.state.status !== "authenticated" || this.state.expiresAt - this.now() <= marginMs;
  }

  signedIn(auth: AuthResponse): void {
    this.set({
      status: "authenticated",
      user: auth.user,
      accessToken: auth.accessToken,
      expiresAt: this.now() + auth.expiresIn * SECONDS_TO_MS,
    });
  }

  userUpdated(user: UserResponse): void {
    if (this.state.status === "authenticated") {
      this.set({ ...this.state, user });
    }
  }

  signedOut(): void {
    this.set({ status: "anonymous" });
  }

  private set(next: Session): void {
    this.state = next;
    this.listeners.forEach((listener) => listener());
  }
}

export const session = new SessionStore();
