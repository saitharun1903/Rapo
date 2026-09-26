"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import clsx from "clsx";
import { Bell } from "lucide-react";
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { toast } from "sonner";
import { api, unwrap } from "@/lib/api/client";
import { errorMessage } from "@/lib/api/errors";
import type { Role } from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";
import { queryKeys } from "@/lib/queryKeys";
import { REALTIME_SNAPSHOT, useRealtimeSubscription } from "@/lib/realtime/RealtimeProvider";
import { Destinations } from "@/lib/realtime/types";

const LIST_SIZE = 10;
const BADGE_MAX = 9;

export function NotificationBell({ role }: { role: Role }) {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const unread = useQuery({
    queryKey: queryKeys.unreadCount,
    queryFn: () => unwrap(api.GET("/api/notifications", { params: { query: { unreadOnly: true, size: 1 } } })),
    select: (page) => page.totalElements,
    meta: REALTIME_SNAPSHOT,
  });
  const list = useQuery({
    queryKey: queryKeys.notifications,
    queryFn: () => unwrap(api.GET("/api/notifications", {
      params: { query: { size: LIST_SIZE, sort: "createdAt,desc" } },
    })),
    enabled: open,
  });

  const refresh = () => queryClient.invalidateQueries({ queryKey: queryKeys.notifications });
  const markRead = useMutation({
    mutationFn: (id: string) => unwrap(api.POST("/api/notifications/{notificationId}/read", {
      params: { path: { notificationId: id } },
    })),
    onSuccess: refresh,
    onError: (error) => toast.error(errorMessage(error)),
  });
  const markAllRead = useMutation({
    mutationFn: () => unwrap(api.POST("/api/notifications/read-all")),
    onSuccess: refresh,
    onError: (error) => toast.error(errorMessage(error)),
  });

  useRealtimeSubscription(Destinations.notifications, (notification) => {
    void refresh();
    toast(notification.title, { description: notification.body });
  });

  useEffect(() => {
    if (!open) {
      return undefined;
    }
    const close = (event: MouseEvent | KeyboardEvent) => {
      if (event instanceof KeyboardEvent ? event.key === "Escape"
        : !containerRef.current?.contains(event.target as Node)) {
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

  const count = unread.data ?? 0;
  return (
    <div ref={containerRef} className="relative">
      <button type="button" onClick={() => setOpen((value) => !value)} aria-expanded={open} aria-haspopup="true"
        aria-label={count > 0 ? `Notifications, ${count} unread` : "Notifications"}
        className="relative rounded-control p-2 text-fg-muted hover:bg-surface-2 hover:text-fg">
        <Bell className="size-5" aria-hidden />
        {count > 0 && (
          <span className="absolute -right-0.5 -top-0.5 flex size-5 items-center justify-center rounded-full bg-brand text-[10px] font-semibold text-brand-fg">
            {count > BADGE_MAX ? `${BADGE_MAX}+` : count}
          </span>
        )}
      </button>
      {open && (
        <div className="absolute right-0 z-50 mt-2 w-80 overflow-hidden rounded-card border border-line bg-surface shadow-float">
          <div className="flex items-center justify-between border-b border-line px-4 py-3">
            <span className="text-sm font-semibold">Notifications</span>
            <button type="button" className="text-xs font-medium text-brand-strong disabled:opacity-50"
              disabled={count === 0 || markAllRead.isPending} onClick={() => markAllRead.mutate()}>
              Mark all read
            </button>
          </div>
          <ul className="max-h-96 overflow-y-auto">
            {list.isPending && <li className="px-4 py-6 text-center text-sm text-fg-muted">Loading…</li>}
            {list.isError && <li className="px-4 py-6 text-center text-sm text-danger">{errorMessage(list.error)}</li>}
            {list.data?.content.length === 0 && (
              <li className="px-4 py-6 text-center text-sm text-fg-muted">Nothing yet.</li>
            )}
            {list.data?.content.map((item) => (
              <li key={item.id} className={clsx("border-b border-line px-4 py-3 last:border-0", !item.read && "bg-brand-soft/60")}>
                <p className="text-sm font-semibold text-fg">{item.title}</p>
                <p className="text-sm text-fg-muted">{item.body}</p>
                <div className="mt-1 flex items-center gap-3 text-xs text-fg-muted">
                  <span>{formatDateTime(item.createdAt)}</span>
                  {item.rideId && role === "PASSENGER" && (
                    <Link href={`/trips/${item.rideId}`} className="font-medium text-brand-strong" onClick={() => setOpen(false)}>
                      View trip
                    </Link>
                  )}
                  {!item.read && (
                    <button type="button" className="font-medium text-brand-strong" onClick={() => markRead.mutate(item.id)}>
                      Mark read
                    </button>
                  )}
                </div>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
