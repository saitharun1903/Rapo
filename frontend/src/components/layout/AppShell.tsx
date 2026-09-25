"use client";

import clsx from "clsx";
import { LogOut, Monitor, Moon, Settings, Sun, WifiOff } from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useTheme } from "next-themes";
import { logout } from "@/lib/api/client";
import type { Role, UserResponse } from "@/lib/api/types";
import { useRealtime } from "@/lib/realtime/RealtimeProvider";
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
    { href: "/drive/earnings", label: "Earnings" },
    { href: "/drive/onboarding", label: "Profile & vehicle" },
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
  { value: "light", label: "Light theme", icon: Sun },
  { value: "dark", label: "Dark theme", icon: Moon },
  { value: "system", label: "System theme", icon: Monitor },
] as const;

function isActive(pathname: string, item: NavItem): boolean {
  return item.exact ? pathname === item.href : pathname === item.href || pathname.startsWith(`${item.href}/`);
}

function ThemeToggle() {
  const { theme, setTheme } = useTheme();
  const index = Math.max(0, THEMES.findIndex((option) => option.value === theme));
  const next = THEMES[(index + 1) % THEMES.length];
  const Current = THEMES[index].icon;
  return (
    <button type="button" onClick={() => setTheme(next.value)} aria-label={`${THEMES[index].label}. Switch to ${next.label.toLowerCase()}`}
      className="rounded-xl p-2 text-fg-muted hover:bg-surface-2 hover:text-fg">
      <Current className="size-5" aria-hidden />
    </button>
  );
}

function ConnectionBanner() {
  const { state } = useRealtime();
  if (state !== "reconnecting") {
    return null;
  }
  return (
    <div role="status" className="flex items-center justify-center gap-2 bg-warning-soft px-4 py-1.5 text-sm font-medium text-warning">
      <WifiOff className="size-4" aria-hidden /> Reconnecting… live updates are paused.
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
      <header className="sticky top-0 z-40 border-b border-line bg-surface/90 backdrop-blur">
        <div className="mx-auto flex h-16 max-w-7xl items-center gap-4 px-4">
          <Link href="/" aria-label="RideFlow home"><Logo /></Link>
          <nav aria-label="Main" className="flex flex-1 items-center gap-1 overflow-x-auto">
            {items.map((item) => (
              <Link key={item.href} href={item.href} aria-current={isActive(pathname, item) ? "page" : undefined}
                className={clsx("whitespace-nowrap rounded-xl px-3 py-2 text-sm font-medium transition-colors",
                  isActive(pathname, item) ? "bg-brand-soft text-brand" : "text-fg-muted hover:bg-surface-2 hover:text-fg")}>
                {item.label}
              </Link>
            ))}
          </nav>
          <NotificationBell role={user.role} />
          <ThemeToggle />
          <Link href="/settings" aria-label="Account settings" className="rounded-xl p-2 text-fg-muted hover:bg-surface-2 hover:text-fg">
            <Settings className="size-5" aria-hidden />
          </Link>
          <span className="hidden text-sm font-medium text-fg md:inline">{user.fullName}</span>
          <button type="button" onClick={signOut} aria-label="Sign out" className="rounded-xl p-2 text-fg-muted hover:bg-surface-2 hover:text-fg">
            <LogOut className="size-5" aria-hidden />
          </button>
        </div>
        <ConnectionBanner />
      </header>
      <main className="flex-1">{children}</main>
    </div>
  );
}
