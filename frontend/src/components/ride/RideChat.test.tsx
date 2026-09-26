import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useEffect } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api/client";
import type { RideMessage } from "@/lib/api/types";
import { answer } from "@/test/api";
import { RideChat, RideChatButton, useRideChat, withMessage } from "./RideChat";

vi.mock("@/lib/api/client", async (importOriginal) => ({
  ...await importOriginal<typeof import("@/lib/api/client")>(),
  api: { GET: vi.fn(), POST: vi.fn() },
}));

vi.mock("@/lib/realtime/RealtimeProvider", () => ({
  REALTIME_SNAPSHOT: { realtime: true },
  useRealtimeSubscription: () => undefined,
}));

const get = vi.mocked(api.GET);
const post = vi.mocked(api.POST);

function message(id: string, senderRole: "PASSENGER" | "DRIVER", body: string): RideMessage {
  return { id, rideId: "r1", senderId: senderRole === "PASSENGER" ? "p1" : "d1", senderRole, body, sentAt: "2026-09-25T10:00:00Z" };
}

function Chat({ canSend = true, openOnMount = false }: { canSend?: boolean; openOnMount?: boolean }) {
  const chat = useRideChat("r1", "PASSENGER", true);
  const { setOpen } = chat;
  useEffect(() => {
    if (openOnMount) {
      setOpen(true);
    }
    // Only on mount.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  return (
    <>
      <RideChatButton chat={chat} label="Message Ravi" />
      <RideChat rideId="r1" me="PASSENGER" otherName="Ravi" canSend={canSend} chat={chat} />
    </>
  );
}

function renderChat(props: { canSend?: boolean; openOnMount?: boolean } = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={client}><Chat {...props} /></QueryClientProvider>);
}

beforeEach(() => {
  get.mockReset();
  post.mockReset();
});

describe("withMessage", () => {
  it("adds a new message once, whichever of the POST and the push arrives first", () => {
    const first = message("m1", "PASSENGER", "Hi");
    const once = withMessage(undefined, first);
    expect(withMessage(once, first)).toBe(once);
    expect(withMessage(once, message("m2", "DRIVER", "Hello"))).toHaveLength(2);
  });
});

describe("RideChat", () => {
  it("counts the other side's messages as unread until the chat is opened", async () => {
    answer(get, [message("m1", "DRIVER", "I'm at the gate"), message("m2", "PASSENGER", "Coming")]);
    renderChat();

    expect(await screen.findByRole("button", { name: "Message Ravi, 1 unread" })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: /Message Ravi/ }));

    expect(screen.getByRole("dialog", { name: "Chat with Ravi" })).toBeInTheDocument();
    expect(screen.getByText("I'm at the gate")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Close" }));
    expect(screen.getByRole("button", { name: "Message Ravi" })).toBeInTheDocument();
  });

  it("sends a quick reply and shows it once", async () => {
    answer(get, []);
    answer(post, message("m3", "PASSENGER", "Coming now"), 201);
    renderChat({ openOnMount: true });

    expect(await screen.findByText(/No messages yet/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Coming now" }));

    expect(post).toHaveBeenCalledWith("/api/rides/{rideId}/messages",
      { params: { path: { rideId: "r1" } }, body: { body: "Coming now" } });
    expect(await screen.findAllByText("Coming now", { selector: "span *, span" })).not.toHaveLength(0);
  });

  it("does not send a blank message", async () => {
    answer(get, []);
    renderChat({ openOnMount: true });

    await userEvent.type(await screen.findByLabelText("Message"), "   ");

    expect(screen.getByRole("button", { name: "Send message" })).toBeDisabled();
  });

  it("offers no way to write once the ride is over", async () => {
    answer(get, [message("m1", "DRIVER", "Thanks")]);
    renderChat({ canSend: false, openOnMount: true });

    expect(await screen.findByText("This conversation has ended.")).toBeInTheDocument();
    expect(screen.queryByLabelText("Message")).not.toBeInTheDocument();
  });
});
