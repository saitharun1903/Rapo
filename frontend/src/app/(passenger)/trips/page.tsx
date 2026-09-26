"use client";

import { TripHistory } from "@/components/trips/TripHistory";
import { ButtonLink } from "@/components/ui/button";

export default function TripsPage() {
  return (
    <TripHistory title="Your trips" description="Every ride you booked, newest first."
      empty={{ title: "No trips yet", body: "Your completed and cancelled rides will appear here.", action: <ButtonLink href="/ride">Book a ride</ButtonLink> }}
      hrefFor={(trip) => `/trips/${trip.id}`} />
  );
}
