"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import clsx from "clsx";
import { Banknote, CreditCard, TrendingUp } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { LazyMap } from "@/components/map/LazyMap";
import { Button } from "@/components/ui/button";
import { Badge, ErrorState, Skeleton } from "@/components/ui/surface";
import { api, unwrap, unwrapOptional } from "@/lib/api/client";
import { errorMessage, isApiError } from "@/lib/api/errors";
import type { FareQuoteResponse, GeoPoint, PaymentMethod, VehicleCategory } from "@/lib/api/types";
import { config } from "@/lib/config";
import { formatDistance, formatDuration, formatMoney, humanize } from "@/lib/format";
import { currentPosition, toPoint } from "@/lib/geolocation";
import { queryKeys } from "@/lib/queryKeys";
import { PlaceField, type Place } from "./PlaceField";

const NEARBY_REFRESH_MS = 15_000;
const NEARBY_RADIUS_METERS = 3_000;
const COORDINATE_DECIMALS = 5;

const PAYMENT_METHODS: { value: PaymentMethod; label: string; note: string; icon: typeof Banknote }[] = [
  { value: "CASH", label: "Cash", note: "Pay the driver", icon: Banknote },
  { value: "CARD", label: "Card", note: "Sandbox: no real charge", icon: CreditCard },
];

type Field = "pickup" | "dropoff";

const keyOf = (point: GeoPoint) => `${point.lat.toFixed(COORDINATE_DECIMALS)},${point.lng.toFixed(COORDINATE_DECIMALS)}`;

const coordinates = (point: GeoPoint) => `${point.lat.toFixed(COORDINATE_DECIMALS)}, ${point.lng.toFixed(COORDINATE_DECIMALS)}`;

/**
 * A chosen point with a readable address. The point is what matters for booking, so when the geocoder is
 * down or rate limited the coordinates stand in for the address rather than blocking the booking.
 */
async function describe(point: GeoPoint): Promise<Place> {
  try {
    const place = await unwrapOptional(api.GET("/api/geo/reverse", { params: { query: point } }));
    return { point, address: place?.address ?? coordinates(point) };
  } catch (error) {
    if (isApiError(error, "GEOCODING_UNAVAILABLE", "RATE_LIMITED")) {
      toast.info("Address lookup is busy, so the coordinates are used instead.");
      return { point, address: coordinates(point) };
    }
    throw error;
  }
}

export function BookingView() {
  const queryClient = useQueryClient();
  const [pickup, setPickup] = useState<Place | null>(null);
  const [dropoff, setDropoff] = useState<Place | null>(null);
  const [picking, setPicking] = useState<Field | null>(null);
  const [locating, setLocating] = useState(false);
  const [category, setCategory] = useState<VehicleCategory | null>(null);
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>("CASH");

  const near = pickup?.point ?? config.mapCenter;
  const nearby = useQuery({
    queryKey: queryKeys.nearby(near.lat, near.lng),
    queryFn: () => unwrap(api.GET("/api/drivers/nearby", {
      params: { query: { lat: near.lat, lng: near.lng, radiusMeters: NEARBY_RADIUS_METERS } },
    })),
    refetchInterval: NEARBY_REFRESH_MS,
  });

  const estimateKey = pickup && dropoff ? `${keyOf(pickup.point)}>${keyOf(dropoff.point)}` : null;
  const estimate = useQuery({
    queryKey: queryKeys.estimate(estimateKey ?? "none"),
    queryFn: () => unwrap(api.POST("/api/fares/estimate", { body: { pickup: pickup!.point, dropoff: dropoff!.point } })),
    enabled: estimateKey !== null,
  });
  const quotes = estimate.data?.quotes ?? [];
  const selected: FareQuoteResponse | undefined = quotes.find((quote) => quote.vehicleCategory === category) ?? quotes[0];

  const book = useMutation({
    mutationFn: (quote: FareQuoteResponse) => unwrap(api.POST("/api/rides", {
      body: {
        quoteId: quote.quoteId,
        pickup: { point: pickup!.point, address: pickup!.address },
        dropoff: { point: dropoff!.point, address: dropoff!.address },
        paymentMethod,
      },
    })),
    onSuccess: (ride) => queryClient.setQueryData(queryKeys.activeRide, ride),
    onError: (error) => {
      if (isApiError(error, "QUOTE_EXPIRED", "QUOTE_INVALID", "QUOTE_MISMATCH")) {
        toast.info("Prices were refreshed. Check the fare and request again.");
        void estimate.refetch();
      } else if (isApiError(error, "ACTIVE_RIDE_EXISTS")) {
        void queryClient.invalidateQueries({ queryKey: queryKeys.activeRide });
      } else {
        toast.error(errorMessage(error));
      }
    },
  });

  const set = (field: Field, place: Place) => {
    (field === "pickup" ? setPickup : setDropoff)(place);
    setPicking(null);
  };

  const onPick = async (point: GeoPoint) => {
    if (!picking) {
      return;
    }
    const field = picking;
    try {
      set(field, await describe(point));
    } catch (error) {
      toast.error(errorMessage(error));
    }
  };

  const useMyLocation = async () => {
    setLocating(true);
    try {
      set("pickup", await describe(toPoint(await currentPosition())));
    } catch (error) {
      toast.error(error instanceof Error ? error.message : errorMessage(error));
    } finally {
      setLocating(false);
    }
  };

  const fitTo = [pickup?.point, dropoff?.point].filter((point): point is GeoPoint => Boolean(point));

  return (
    <div className="grid h-[calc(100dvh-4rem)] grid-rows-[1fr_minmax(0,1.2fr)] lg:grid-cols-[26rem_1fr] lg:grid-rows-1">
      <section aria-label="Book a ride" className="order-2 overflow-y-auto border-line bg-surface p-4 lg:order-1 lg:border-r">
        <h1 className="mb-4 text-xl font-bold tracking-tight">Where to?</h1>
        <div className="flex flex-col gap-3">
          <PlaceField label="Pickup" tone="brand" value={pickup} onChange={(place) => set("pickup", place)} near={near}
            picking={picking === "pickup"} onPickOnMap={() => setPicking(picking === "pickup" ? null : "pickup")}
            onUseMyLocation={useMyLocation} locating={locating} />
          <PlaceField label="Destination" tone="accent" value={dropoff} onChange={(place) => set("dropoff", place)} near={near}
            picking={picking === "dropoff"} onPickOnMap={() => setPicking(picking === "dropoff" ? null : "dropoff")} />
        </div>

        <p className="mt-3 text-xs text-fg-muted" aria-live="polite">
          {nearby.data ? `${nearby.data.length} driver${nearby.data.length === 1 ? "" : "s"} near ${pickup ? "your pickup" : "the city centre"}` : ""}
        </p>

        {estimateKey && (
          <div className="mt-5">
            <h2 className="mb-2 text-sm font-semibold text-fg">Choose a ride</h2>
            {estimate.isPending && <div className="flex flex-col gap-2"><Skeleton className="h-16" /><Skeleton className="h-16" /></div>}
            {estimate.isError && (
              isApiError(estimate.error, "OUTSIDE_SERVICE_AREA", "PICKUP_EQUALS_DROPOFF")
                ? <p role="alert" className="rounded-xl bg-warning-soft p-3 text-sm text-warning">{errorMessage(estimate.error)}</p>
                : <ErrorState error={estimate.error} onRetry={() => void estimate.refetch()} title="Could not price this trip" />
            )}
            {estimate.data && (
              <>
                <p className="mb-2 flex flex-wrap items-center gap-2 text-xs text-fg-muted">
                  {formatDistance(estimate.data.distanceMeters)} · about {formatDuration(estimate.data.durationSeconds)}
                  {estimate.data.estimateSource === "APPROXIMATE" && <Badge tone="warning">Approximate route</Badge>}
                  {Number(estimate.data.surgeMultiplier) > 1 && (
                    <Badge tone="accent"><TrendingUp className="size-3" aria-hidden /> High demand {estimate.data.surgeMultiplier}×</Badge>
                  )}
                </p>
                <div role="radiogroup" aria-label="Vehicle category" className="flex flex-col gap-2">
                  {quotes.map((quote) => (
                    <button key={quote.quoteId} type="button" role="radio" aria-checked={selected?.vehicleCategory === quote.vehicleCategory}
                      onClick={() => setCategory(quote.vehicleCategory)}
                      className={clsx("flex items-center justify-between rounded-2xl border px-4 py-3 text-left transition-colors",
                        selected?.vehicleCategory === quote.vehicleCategory ? "border-brand bg-brand-soft" : "border-line hover:bg-surface-2")}>
                      <span className="font-semibold text-fg">{humanize(quote.vehicleCategory)}</span>
                      <span className="text-base font-bold tabular-nums text-fg">{formatMoney(quote.estimatedFare)}</span>
                    </button>
                  ))}
                </div>
                <div role="radiogroup" aria-label="Payment method" className="mt-4 grid grid-cols-2 gap-2">
                  {PAYMENT_METHODS.map(({ value, label, note, icon: Icon }) => (
                    <button key={value} type="button" role="radio" aria-checked={paymentMethod === value} onClick={() => setPaymentMethod(value)}
                      className={clsx("flex items-center gap-2 rounded-xl border px-3 py-2 text-left",
                        paymentMethod === value ? "border-brand bg-brand-soft" : "border-line hover:bg-surface-2")}>
                      <Icon className="size-4 text-fg-muted" aria-hidden />
                      <span><span className="block text-sm font-semibold text-fg">{label}</span><span className="block text-xs text-fg-muted">{note}</span></span>
                    </button>
                  ))}
                </div>
                <Button size="lg" className="mt-4 w-full" disabled={!selected} loading={book.isPending}
                  onClick={() => selected && book.mutate(selected)}>
                  Request {selected ? humanize(selected.vehicleCategory) : "ride"}
                </Button>
                <p className="mt-2 text-center text-xs text-fg-muted">The final fare uses the route actually driven, at this demand multiplier.</p>
              </>
            )}
          </div>
        )}
      </section>
      <div className="order-1 lg:order-2">
        <LazyMap label="Map for choosing pickup and destination" pickup={pickup?.point} dropoff={dropoff?.point}
          route={estimate.data?.route} nearby={nearby.data?.map((driver) => driver.position)} fitTo={fitTo}
          onPick={picking ? onPick : undefined} />
      </div>
    </div>
  );
}
