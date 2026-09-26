"use client";

import { TripHistory } from "@/components/trips/TripHistory";
import { ButtonLink } from "@/components/ui/button";

export default function DriverTripsPage() {
  return (
    <TripHistory title="Your trips" description="Every ride you were assigned, newest first. Fares are before the platform fee."
      empty={{ title: "No trips yet", body: "Rides you accept will appear here.", action: <ButtonLink href="/drive">Go to the console</ButtonLink> }} />
  );
}
