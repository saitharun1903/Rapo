"use client";

import clsx from "clsx";
import { CheckCircle2, CloudOff, LogOut, Settings, WifiOff } from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useTheme } from "next-themes";
import { useEffect, useRef, useState } from "react";
import { Segmented } from "@/components/ui/segmented";
import { logout } from "@/lib/api/client";
import type { Role, UserResponse } from "@/lib/api/types";
import { useRealtime } from "@/lib/realtime/RealtimeProvider";
import { useConnectionNotice } from "@/lib/realtime/useConnectionNotice";
import { humanize } from "@/lib/format";
import { Logo } from "./Logo";
import { NotificationBell } from "./NotificationBell";

type NavItem = { href: string; label: string; exact?: boolean };

const NAVIGATION: Record<Role, NavItem[]> = {
  PASSENGER: [
    { href: "/ride", label: "Ride" },
    { href: "/trips", label: "Trips" },
  ],
  DRIVER: [
    { href: "/drive", label: "Drive", exact: true },
    { href: "/drive/trips", label: "Trips" },
    { href: "/drive/earnings", label: "Earnings" },
    { href: "/drive/onboarding", label: "Vehicle" },
  ],
  ADMIN: [
    { href: "/admin", label: "Overview", exact: true },
    { href: "/admin/rides", label: "Rides" },
    { href: "/admin/drivers", label: "Drivers" },
    { href: "/admin/users", label: "Users" },
    { href: "/admin/system", label: "System" },
    { href: "/admin/audit", label: "Audit log" },
  ],
};

const THEMES = [
  { value: "light", label: "Light" },
  { value: "dark", label: "Dark" },
  { value: "system", label: "Auto" },
] as const;

type ThemeChoice = (typeof THEMES)[number]["value"];

function isActive(pathname: string, item: NavItem): boolean {
  return item.exact ? pathname === item.href : pathname === item.href || pathname.startsWith(`${item.href}/`);
}

function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  const first = parts[0]?.[0] ?? "";
  const last = parts.length > 1 ? parts[parts.length - 1][0] : "";
  return (first + last).toUpperCase() || "?";
}

/** Whether live updates are flowing: a quiet dot when they are, words when they are not. */
function LiveIndicator() {
  const { state } = useRealtime();
  const live = state === "connected";
  const label = live ? "Live updates on" : state === "connecting" || state === "idle" ? "Connecting to live updates" : "Live updates paused";
  return (
    <span className="hidden items-center gap-1.5 text-xs font-medium text-fg-muted sm:inline-flex" title={label}>
      <span className="relative flex size-2">
        {live && <span className="absolute inset-0 animate-ping rounded-full bg-success opacity-40 motion-reduce:hidden" />}
        <span className={clsx("relative size-2 rounded-full", live ? "bg-success" : "bg-line-strong")} />
      </span>
      <span className="sr-only">{label}</span>
      <span aria-hidden>{live ? "Live" : "Offline"}</span>
    </span>
  );
}

function ConnectionBanner() {
  const { state } = useRealtime();
  const notice = useConnectionNotice(state);
  if (notice === null) {
    return null;
  }
  const content = {
    offline: { icon: CloudOff, text: "You're offline. Your ride is saved; live updates resume when you reconnect.", tone: "warning" },
    reconnecting: { icon: WifiOff, text: "Reconnecting… live updates are paused.", tone: "warning" },
    unavailable: { icon: WifiOff, text: "Live updates are unavailable; reload the page to see changes.", tone: "warning" },
    restored: { icon: CheckCircle2, text: "Connection restored.", tone: "success" },
  }[notice];
  const Icon = content.icon;
  return (
    <div role="status" className={clsx("flex items-center justify-center gap-2 px-4 py-1.5 text-sm font-medium animate-fade",
      content.tone === "success" ? "bg-success-soft text-success" : "bg-warning-soft text-warning")}>
      <Icon className="size-4" aria-hidden /> {content.text}
    </div>
  );
}

function AccountMenu({ user, onSignOut }: { user: UserResponse; onSignOut: () => void }) {
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const { theme, setTheme } = useTheme();

  useEffect(() => {
    if (!open) {
      return;
    }
    const close = (event: MouseEvent | KeyboardEvent) => {
      if (event instanceof KeyboardEvent) {
        if (event.key === "Escape") {
          setOpen(false);
          buttonRef.current?.focus();
        }
        return;
      }
      if (!containerRef.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener("mousedown", close);
    document.addEventListener("keydown", close);
    return () => {
      document.removeEventListener("mousedown", close);
      document.removeEventListener("keydown", close);
    };
  }, [open]);

  return (
    <div ref={containerRef} className="relative">
      <button ref={buttonRef} type="button" onClick={() => setOpen((value) => !value)} aria-expanded={open} aria-haspopup="true"
        aria-label={`Account: ${user.fullName}`}
        className="flex size-9 items-center justify-center rounded-control bg-surface-2 text-xs font-semibold tracking-wide text-fg transition-colors hover:bg-line">
        {initials(user.fullName)}
      </button>
      {open && (
        <div className="absolute right-0 z-[var(--z-sheet)] mt-2 w-72 animate-rise overflow-hidden rounded-card border border-line bg-surface shadow-float">
          <div className="border-b border-line px-4 py-3">
            <p className="truncate text-sm font-medium text-fg">{user.fullName}</p>
            <p className="truncate text-xs text-fg-muted">{user.email} · {humanize(user.role)}</p>
          </div>
          <div className="flex flex-col gap-2 border-b border-line px-4 py-3">
            <span className="eyebrow">Appearance</span>
            <Segmented label="Theme" options={THEMES} value={(theme ?? "system") as ThemeChoice} onChange={setTheme} />
          </div>
          <div className="p-1.5">
            <Link href="/settings" onClick={() => setOpen(false)}
              className="flex items-center gap-2.5 rounded-control px-2.5 py-2 text-sm text-fg hover:bg-surface-2">
              <Settings className="size-4 text-fg-muted" aria-hidden /> Settings
            </Link>
            <button type="button" onClick={onSignOut}
              className="flex w-full items-center gap-2.5 rounded-control px-2.5 py-2 text-left text-sm text-fg hover:bg-surface-2">
              <LogOut className="size-4 text-fg-muted" aria-hidden /> Sign out
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

export function AppShell({ user, children }: { user: UserResponse; children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const items = NAVIGATION[user.role];

  const signOut = async () => {
    await logout();
    router.replace("/login");
  };

  return (
    <div className="flex min-h-full flex-col">
      <header className="sticky top-0 z-[var(--z-header)] border-b border-line bg-bg/85 backdrop-blur-md">
        <div className="mx-auto flex h-14 max-w-[1440px] items-center gap-4 px-4 sm:gap-6 sm:px-6">
          <Link href="/" aria-label="Raido home" className="shrink-0"><Logo wordmarkClassName="max-[420px]:hidden" /></Link>
          <nav aria-label="Main" className="-mb-px flex h-full min-w-0 flex-1 items-stretch gap-4 overflow-x-auto sm:gap-5">
            {items.map((item) => {
              const active = isActive(pathname, item);
              return (
                <Link key={item.href} href={item.href} aria-current={active ? "page" : undefined}
                  className={clsx("flex items-center whitespace-nowrap border-b-2 text-sm font-medium transition-colors",
                    active ? "border-fg text-fg" : "border-transparent text-fg-muted hover:text-fg")}>
                  {item.label}
                </Link>
              );
            })}
          </nav>
          <div className="flex shrink-0 items-center gap-2">
            <LiveIndicator />
            <NotificationBell role={user.role} />
            <AccountMenu user={user} onSignOut={signOut} />
          </div>
        </div>
        <ConnectionBanner />
      </header>
      <main id="main" className="flex-1">{children}</main>
    </div>
  );
}
