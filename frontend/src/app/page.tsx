"use client";

import { Brain, MapPinned, ShieldCheck, Zap } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { Logo } from "@/components/layout/Logo";
import { ButtonLink } from "@/components/ui/button";
import { homeFor, useSession } from "@/lib/auth/useSession";

const FEATURES = [
  { icon: MapPinned, title: "Live tracking", text: "Watch your driver approach in real time, with an ETA from the actual road network." },
  { icon: Zap, title: "Fair, explained fares", text: "A signed quote before you book, and the final fare from the route actually driven." },
  { icon: Brain, title: "Trip insights", text: "Ask why a trip cost what it did. Answers only use your trip's recorded data." },
  { icon: ShieldCheck, title: "Built to be safe", text: "Verified drivers, private by default, and every sensitive action audited." },
];

export default function LandingPage() {
  const current = useSession();
  const router = useRouter();

  useEffect(() => {
    if (current.status === "authenticated") {
      router.replace(homeFor(current.user.role));
    }
  }, [current, router]);

  return (
    <div className="min-h-full bg-bg">
      <header className="mx-auto flex max-w-6xl items-center justify-between px-6 py-5">
        <Logo />
        <div className="flex gap-2">
          <ButtonLink href="/login" variant="ghost">Sign in</ButtonLink>
          <ButtonLink href="/register">Create account</ButtonLink>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-6 pb-20 pt-10">
        <section className="grid items-center gap-10 md:grid-cols-2">
          <div>
            <p className="mb-3 inline-flex rounded-full bg-brand-soft px-3 py-1 text-sm font-semibold text-brand-strong">
              Rides in Hyderabad
            </p>
            <h1 className="text-4xl font-extrabold leading-tight tracking-tight text-fg md:text-5xl">
              Get there with a ride that shows its work.
            </h1>
            <p className="mt-4 max-w-md text-lg text-fg-muted">
              Raido matches you with a nearby verified driver, tracks the trip live and explains every rupee of the fare.
            </p>
            <div className="mt-8 flex flex-wrap gap-3">
              <ButtonLink href="/register" size="lg">Book your first ride</ButtonLink>
              <ButtonLink href="/register?as=driver" size="lg" variant="secondary">Drive with Raido</ButtonLink>
            </div>
          </div>
          <div aria-hidden className="relative aspect-square overflow-hidden rounded-sheet border border-line bg-surface shadow-raise">
            <svg viewBox="0 0 400 400" className="size-full">
              <defs>
                <pattern id="grid" width="40" height="40" patternUnits="userSpaceOnUse">
                  <path d="M40 0H0V40" fill="none" className="stroke-line" strokeWidth="1" />
                </pattern>
              </defs>
              <rect width="400" height="400" fill="url(#grid)" />
              <path d="M70 320 C 140 320, 120 190, 200 190 S 270 80, 330 80" fill="none" className="stroke-brand"
                strokeWidth="8" strokeLinecap="round" />
              <circle cx="70" cy="320" r="14" className="fill-brand" />
              <circle cx="70" cy="320" r="6" fill="white" />
              <circle cx="330" cy="80" r="14" className="fill-ink" />
              <circle cx="330" cy="80" r="6" fill="white" />
              <rect x="186" y="176" width="28" height="28" rx="8" className="fill-fg" />
            </svg>
          </div>
        </section>
        <section aria-label="Why Raido" className="mt-20 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {FEATURES.map(({ icon: Icon, title, text }) => (
            <div key={title} className="rounded-card border border-line bg-surface p-5">
              <Icon className="size-6 text-brand-strong" aria-hidden />
              <h2 className="mt-3 font-semibold text-fg">{title}</h2>
              <p className="mt-1 text-sm text-fg-muted">{text}</p>
            </div>
          ))}
        </section>
      </main>
    </div>
  );
}
