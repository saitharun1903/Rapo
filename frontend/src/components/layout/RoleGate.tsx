"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect } from "react";
import type { Role } from "@/lib/api/types";
import { homeFor, useSession } from "@/lib/auth/useSession";
import { RealtimeProvider } from "@/lib/realtime/RealtimeProvider";
import { Skeleton } from "@/components/ui/surface";
import { AppShell } from "./AppShell";

/**
 * Shows its children only to a signed-in user with one of `roles`, inside the app shell with a live
 * connection. Anyone else is sent to sign in, or to their own home. This is navigation, not security: the
 * backend authorises every request.
 */
export function RoleGate({ roles, children }: { roles: readonly Role[]; children: React.ReactNode }) {
  const current = useSession();
  const router = useRouter();
  const pathname = usePathname();
  const allowed = current.status === "authenticated" && roles.includes(current.user.role);

  useEffect(() => {
    if (current.status === "anonymous") {
      router.replace(`/login?next=${encodeURIComponent(pathname)}`);
    } else if (current.status === "authenticated" && !roles.includes(current.user.role)) {
      router.replace(homeFor(current.user.role));
    }
  }, [current, pathname, roles, router]);

  if (!allowed) {
    return (
      <div role="status" aria-label="Loading your account" className="mx-auto flex max-w-7xl flex-col gap-4 p-6">
        <Skeleton className="h-10 w-48" />
        <Skeleton className="h-64" />
      </div>
    );
  }
  return (
    <RealtimeProvider>
      <AppShell user={current.user}>{children}</AppShell>
    </RealtimeProvider>
  );
}
