"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import clsx from "clsx";
import { MessageCircle, SendHorizontal } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { api, unwrap } from "@/lib/api/client";
import { errorMessage, isApiError } from "@/lib/api/errors";
import type { RideMessage } from "@/lib/api/types";
import { formatTime } from "@/lib/format";
import { queryKeys } from "@/lib/queryKeys";
import { REALTIME_SNAPSHOT, useRealtimeSubscription } from "@/lib/realtime/RealtimeProvider";
import { Destinations } from "@/lib/realtime/types";

export const MESSAGE_MAX = 500;

type Side = "PASSENGER" | "DRIVER";

const QUICK_REPLIES: Record<Side, string[]> = {
  PASSENGER: ["I'm at the pickup", "Coming now", "Please wait a minute"],
  DRIVER: ["I'm at the pickup", "On my way", "Running a few minutes late"],
};

/** Adds a message unless it is already there: the sender gets it back both from the POST and from the push. */
export function withMessage(messages: RideMessage[] | undefined, message: RideMessage): RideMessage[] {
  const current = messages ?? [];
  return current.some((existing) => existing.id === message.id) ? current : [...current, message];
}

/**
 * The ride's conversation: the REST list, then pushes. Messages from the other side that arrive while the
 * chat is closed count as unread and raise a toast.
 */
export function useRideChat(rideId: string, me: Side, enabled: boolean) {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [readCount, setReadCount] = useState(0);
  const messages = useQuery({
    queryKey: queryKeys.rideMessages(rideId),
    queryFn: () => unwrap(api.GET("/api/rides/{rideId}/messages", { params: { path: { rideId } } })),
    enabled,
    meta: REALTIME_SNAPSHOT,
  });

  useRealtimeSubscription(Destinations.rideMessages, (message) => {
    if (message.rideId !== rideId) {
      return;
    }
    queryClient.setQueryData<RideMessage[]>(queryKeys.rideMessages(rideId), (current) => withMessage(current, message));
    if (message.senderRole !== me && !open) {
      toast(me === "PASSENGER" ? "Message from your driver" : "Message from your passenger", { description: message.body });
    }
  }, enabled);

  const fromOther = (messages.data ?? []).filter((message) => message.senderRole !== me).length;
  const unread = open ? 0 : Math.max(0, fromOther - readCount);

  const setChatOpen = (next: boolean) => {
    setOpen(next);
    setReadCount(fromOther);
  };

  return { messages: messages.data ?? [], loading: messages.isPending && enabled, unread, open, setOpen: setChatOpen };
}

type RideChatProps = {
  rideId: string;
  me: Side;
  /** The other participant's name, for the title. */
  otherName: string;
  /** Whether new messages can be sent: only while a driver is on the ride. */
  canSend: boolean;
  chat: ReturnType<typeof useRideChat>;
};

export function RideChatButton({ chat, label }: { chat: ReturnType<typeof useRideChat>; label: string }) {
  return (
    <Button variant="secondary" className="relative flex-1" onClick={() => chat.setOpen(true)}
      aria-label={chat.unread > 0 ? `${label}, ${chat.unread} unread` : label}>
      <MessageCircle className="size-4" aria-hidden /> {label}
      {chat.unread > 0 && (
        <span aria-hidden className="num absolute -right-1.5 -top-1.5 flex h-5 min-w-5 items-center justify-center rounded-full bg-brand px-1 text-[11px] font-semibold text-brand-fg">
          {chat.unread}
        </span>
      )}
    </Button>
  );
}

export function RideChat({ rideId, me, otherName, canSend, chat }: RideChatProps) {
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState("");
  const listRef = useRef<HTMLOListElement>(null);

  const send = useMutation({
    mutationFn: (body: string) => unwrap(api.POST("/api/rides/{rideId}/messages", {
      params: { path: { rideId } },
      body: { body },
    })),
    onSuccess: (message) => {
      queryClient.setQueryData<RideMessage[]>(queryKeys.rideMessages(rideId), (current) => withMessage(current, message));
      setDraft("");
    },
    onError: (error) => toast.error(isApiError(error, "CHAT_CLOSED") ? "This conversation has ended." : errorMessage(error)),
  });

  useEffect(() => {
    const list = listRef.current;
    if (list && chat.open) {
      list.scrollTop = list.scrollHeight;
    }
  }, [chat.messages.length, chat.open]);

  const submit = (body: string) => {
    const text = body.trim();
    if (text !== "" && !send.isPending) {
      send.mutate(text);
    }
  };

  return (
    <Dialog open={chat.open} onClose={() => chat.setOpen(false)} title={`Chat with ${otherName}`}>
      <ol ref={listRef} aria-label="Messages" aria-live="polite" className="-mx-1 flex max-h-[50dvh] min-h-40 flex-col gap-2 overflow-y-auto px-1 py-1">
        {chat.loading && <li className="text-sm text-fg-muted">Loading messages…</li>}
        {!chat.loading && chat.messages.length === 0 && (
          <li className="py-6 text-sm text-fg-muted">
            No messages yet. Messages go only to {otherName} and are deleted 30 days after the ride.
          </li>
        )}
        {chat.messages.map((message) => {
          const mine = message.senderRole === me;
          return (
            <li key={message.id} className={clsx("flex max-w-[85%] flex-col gap-0.5", mine ? "items-end self-end" : "items-start self-start")}>
              <span className={clsx("rounded-card px-3 py-2 text-sm", mine ? "bg-ink text-ink-fg" : "bg-surface-2 text-fg")}>
                <span className="sr-only">{mine ? "You" : otherName}: </span>{message.body}
              </span>
              <span className="num text-[11px] text-fg-muted">{formatTime(message.sentAt)}</span>
            </li>
          );
        })}
      </ol>
      {canSend ? (
        <>
          <div className="mt-3 flex flex-wrap gap-1.5">
            {QUICK_REPLIES[me].map((reply) => (
              <button key={reply} type="button" onClick={() => submit(reply)} disabled={send.isPending}
                className="rounded-full border border-line-strong px-3 py-1 text-xs font-medium text-fg transition-colors hover:bg-surface-2 disabled:opacity-50">
                {reply}
              </button>
            ))}
          </div>
          <form className="mt-3 flex gap-2" onSubmit={(event) => { event.preventDefault(); submit(draft); }}>
            <label className="sr-only" htmlFor={`chat-${rideId}`}>Message</label>
            <input id={`chat-${rideId}`} value={draft} maxLength={MESSAGE_MAX} autoComplete="off" placeholder="Write a message"
              onChange={(event) => setDraft(event.target.value)}
              className="h-11 min-w-0 flex-1 rounded-control border border-line-strong bg-surface px-3 text-sm text-fg placeholder:text-fg-muted/80 focus:border-fg focus:outline-none" />
            <Button type="submit" size="lg" className="w-12 px-0" aria-label="Send message" loading={send.isPending}
              disabled={draft.trim() === ""}>
              {!send.isPending && <SendHorizontal className="size-4" aria-hidden />}
            </Button>
          </form>
        </>
      ) : (
        <p className="mt-3 text-sm text-fg-muted">This conversation has ended.</p>
      )}
    </Dialog>
  );
}
