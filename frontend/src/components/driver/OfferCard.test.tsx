import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { RideOffer } from "@/lib/api/types";
import { OfferCard } from "./OfferCard";

const NOW = Date.parse("2026-09-25T10:00:00Z");

const offer: RideOffer = {
  offerId: "o1",
  rideId: "r1",
  round: 1,
  distanceToPickupMeters: 850,
  pickup: { point: { lat: 17.44, lng: 78.38 }, address: "Hitech City Metro" },
  dropoff: { point: { lat: 17.42, lng: 78.47 }, address: "Hussain Sagar" },
  vehicleCategory: "ECONOMY",
  estimatedFare: { amount: "295.00", currency: "INR" },
  estimatedDistanceMeters: 12_800,
  estimatedDurationSeconds: 29 * 60,
  expiresAt: "2026-09-25T10:00:12Z",
};

describe("OfferCard", () => {
  it("shows the fare, distances and the seconds left, and accepts", async () => {
    const onAccept = vi.fn();
    render(<OfferCard offer={offer} now={NOW} onAccept={onAccept} onDecline={vi.fn()} accepting={false} declining={false} />);

    expect(screen.getByText("₹295.00")).toBeInTheDocument();
    expect(screen.getByText("850 m to pickup")).toBeInTheDocument();
    expect(screen.getByText("12s")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Accept" }));
    expect(onAccept).toHaveBeenCalledTimes(1);
  });

  it("cannot be accepted once expired", () => {
    render(<OfferCard offer={offer} now={Date.parse(offer.expiresAt)} onAccept={vi.fn()} onDecline={vi.fn()} accepting={false} declining={false} />);
    expect(screen.getByRole("button", { name: "Accept" })).toBeDisabled();
    expect(screen.getByText("0s")).toBeInTheDocument();
  });
});
