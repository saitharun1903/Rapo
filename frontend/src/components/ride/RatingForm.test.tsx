import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api/client";
import { answer } from "@/test/api";
import { RatingForm } from "./RatingForm";

vi.mock("@/lib/api/client", async (importOriginal) => ({
  ...await importOriginal<typeof import("@/lib/api/client")>(),
  api: { POST: vi.fn() },
}));

const post = vi.mocked(api.POST);

function renderForm() {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <RatingForm rideId="r1" subject="driver" />
    </QueryClientProvider>,
  );
}

beforeEach(() => post.mockReset());

describe("RatingForm", () => {
  it("needs a score, then sends it with the trimmed comment", async () => {
    answer(post, { score: 4 }, 201);
    renderForm();
    expect(screen.getByRole("button", { name: "Submit rating" })).toBeDisabled();

    await userEvent.click(screen.getByRole("button", { name: "4 stars" }));
    expect(screen.getByRole("button", { name: "4 stars" })).toHaveAttribute("aria-pressed", "true");
    await userEvent.type(screen.getByLabelText("Comment (optional)"), "  Smooth ride  ");
    await userEvent.click(screen.getByRole("button", { name: "Submit rating" }));

    expect(await screen.findByRole("status")).toHaveTextContent("Thanks, your rating is saved.");
    expect(post).toHaveBeenCalledWith("/api/rides/{rideId}/rating",
      { params: { path: { rideId: "r1" } }, body: { score: 4, comment: "Smooth ride" } });
  });

  it("sends no comment when the box is blank", async () => {
    answer(post, { score: 5 }, 201);
    renderForm();
    await userEvent.click(screen.getByRole("button", { name: "5 stars" }));
    await userEvent.type(screen.getByLabelText("Comment (optional)"), "   ");
    await userEvent.click(screen.getByRole("button", { name: "Submit rating" }));

    await screen.findByRole("status");
    expect(post.mock.calls[0][1]).toMatchObject({ body: { score: 5, comment: null } });
  });

  it("treats a ride rated earlier (in another tab, say) as done", async () => {
    answer(post, { code: "ALREADY_RATED", message: "Already rated" }, 409);
    renderForm();
    await userEvent.click(screen.getByRole("button", { name: "1 star" }));
    await userEvent.click(screen.getByRole("button", { name: "Submit rating" }));

    expect(await screen.findByRole("status")).toHaveTextContent("Thanks, your rating is saved.");
  });

  it("shows other failures and lets the rider try again", async () => {
    answer(post, { code: "RIDE_NOT_COMPLETED", message: "This ride is not completed yet." }, 409);
    renderForm();
    await userEvent.click(screen.getByRole("button", { name: "3 stars" }));
    await userEvent.click(screen.getByRole("button", { name: "Submit rating" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("This ride is not completed yet.");
    expect(screen.getByRole("button", { name: "Submit rating" })).toBeEnabled();
  });
});
