"use client";

import clsx from "clsx";
import { Crosshair, LocateFixed, MapPin, Search } from "lucide-react";
import { useId, useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/form";
import { api, unwrap } from "@/lib/api/client";
import { errorMessage } from "@/lib/api/errors";
import type { GeoPoint, PlaceResponse } from "@/lib/api/types";

export type Place = { point: GeoPoint; address: string };

const MIN_QUERY = 2;

type PlaceFieldProps = {
  label: string;
  tone: "brand" | "accent";
  value: Place | null;
  onChange: (place: Place) => void;
  /** Results are biased towards this point. */
  near: GeoPoint;
  /** Whether a click on the map currently sets this field. */
  picking: boolean;
  onPickOnMap: () => void;
  onUseMyLocation?: () => void;
  locating?: boolean;
};

/**
 * Pickup or destination: search by name (on submit, as the geocoding provider's usage policy requires, not
 * per keystroke), the browser's position, or a point chosen on the map.
 */
export function PlaceField({ label, tone, value, onChange, near, picking, onPickOnMap, onUseMyLocation, locating }: PlaceFieldProps) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<PlaceResponse[] | null>(null);
  const [searching, setSearching] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const inputId = useId();

  const search = async (event: React.FormEvent) => {
    event.preventDefault();
    if (query.trim().length < MIN_QUERY) {
      setError("Type at least two characters.");
      return;
    }
    setSearching(true);
    setError(null);
    try {
      const places = await unwrap(api.GET("/api/geo/search", { params: { query: { q: query.trim(), lat: near.lat, lng: near.lng } } }));
      setResults(places);
    } catch (failure) {
      setResults(null);
      setError(errorMessage(failure));
    } finally {
      setSearching(false);
    }
  };

  const choose = (place: PlaceResponse) => {
    onChange({ point: place.point, address: place.address });
    setResults(null);
    setQuery("");
  };

  return (
    <div className={clsx("rounded-2xl border p-3", picking ? "border-brand ring-2 ring-brand/20" : "border-line")}>
      <div className="flex items-center gap-2">
        <MapPin className={clsx("size-4 shrink-0", tone === "brand" ? "text-brand" : "text-accent")} aria-hidden />
        <label htmlFor={inputId} className="text-xs font-semibold uppercase tracking-wide text-fg-muted">{label}</label>
      </div>
      {value && <p className="mt-1 line-clamp-2 text-sm font-medium text-fg">{value.address}</p>}
      <form onSubmit={search} role="search" className="mt-2 flex gap-2">
        <Input id={inputId} value={query} onChange={(event) => setQuery(event.target.value)}
          placeholder={value ? "Search for another place" : "Search for a place"} autoComplete="off" />
        <Button type="submit" variant="secondary" loading={searching} aria-label={`Search ${label.toLowerCase()}`}>
          {!searching && <Search className="size-4" aria-hidden />}
        </Button>
      </form>
      <div className="mt-2 flex flex-wrap gap-2">
        {onUseMyLocation && (
          <Button size="sm" variant="ghost" onClick={onUseMyLocation} loading={locating}>
            {!locating && <LocateFixed className="size-4" aria-hidden />} Use my location
          </Button>
        )}
        <Button size="sm" variant={picking ? "primary" : "ghost"} onClick={onPickOnMap} aria-pressed={picking}>
          <Crosshair className="size-4" aria-hidden /> {picking ? "Click the map…" : "Choose on map"}
        </Button>
      </div>
      {error && <p role="alert" className="mt-2 text-sm text-danger">{error}</p>}
      {results && (
        <ul className="mt-2 flex max-h-56 flex-col gap-1 overflow-y-auto" aria-label={`${label} results`}>
          {results.length === 0 && <li className="px-2 py-2 text-sm text-fg-muted">No places found.</li>}
          {results.map((place) => (
            <li key={`${place.point.lat},${place.point.lng}`}>
              <button type="button" onClick={() => choose(place)} className="w-full rounded-xl px-2 py-2 text-left hover:bg-surface-2">
                <span className="block text-sm font-semibold text-fg">{place.name}</span>
                <span className="block truncate text-xs text-fg-muted">{place.address}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
