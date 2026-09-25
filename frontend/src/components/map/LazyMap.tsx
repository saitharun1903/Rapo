"use client";

import dynamic from "next/dynamic";
import { Skeleton } from "@/components/ui/surface";

/** MapLibre needs the browser (WebGL, window), so the map is only ever rendered on the client. */
export const LazyMap = dynamic(() => import("./MapView"), {
  ssr: false,
  loading: () => <Skeleton className="size-full min-h-72 rounded-none" />,
});
