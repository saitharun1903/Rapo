"use client";

import { ActiveRideView } from "@/components/ride/ActiveRideView";
import { BookingView } from "@/components/ride/BookingView";
import { RideOutcome } from "@/components/ride/RideOutcome";
import { ErrorState, LoadingBlock } from "@/components/ui/surface";
import { useActiveRide } from "@/lib/ride/useActiveRide";

export default function RidePage() {
  const { active, finished, dismissFinished } = useActiveRide();

  if (active.isPending) {
    return <div className="mx-auto max-w-lg p-6"><LoadingBlock label="Loading your ride" /></div>;
  }
  if (active.isError) {
    return <div className="mx-auto max-w-lg p-6"><ErrorState error={active.error} onRetry={() => void active.refetch()} /></div>;
  }
  if (active.data) {
    return <ActiveRideView ride={active.data} />;
  }
  if (finished) {
    return <RideOutcome ride={finished} onDone={dismissFinished} />;
  }
  return <BookingView />;
}
