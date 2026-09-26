import Link from "next/link";
import { Logo } from "@/components/layout/Logo";
import { config } from "@/lib/config";

/** What Raido does, said plainly: facts about the product, not numbers about it. */
const POINTS = [
  { title: "A price before you ride", body: "Every fare is quoted up front and broken down line by line." },
  { title: "Your trip, live", body: "Follow the car on the map from pickup to drop-off." },
  { title: "Safety that notices", body: "An unusual stop gets a check-in, and every alert is on record." },
];

export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="grid min-h-dvh lg:grid-cols-[minmax(0,1fr)_minmax(0,1.1fr)]">
      <div className="flex flex-col px-5 py-6 sm:px-10">
        <Link href="/" aria-label="Raido home" className="self-start"><Logo /></Link>
        <main id="main" className="mx-auto flex w-full max-w-sm flex-1 flex-col justify-center py-12">
          {config.backendConfigured ? children : (
            <div role="status" className="flex flex-col gap-2">
              <h1 className="text-2xl font-semibold tracking-[-0.03em]">Not available yet</h1>
              <p className="text-sm text-fg-muted">
                This deployment has no backend configured yet, so signing in and creating accounts are unavailable.
              </p>
            </div>
          )}
        </main>
      </div>
      <aside aria-hidden className="relative hidden overflow-hidden bg-[#121311] text-[#fafaf7] lg:block dark:border-l dark:border-line dark:bg-[#161714]">
        <RouteArt />
        <div className="relative flex h-full flex-col justify-end gap-10 p-12">
          <p className="max-w-md text-[2.6rem] font-semibold leading-[1.02] tracking-[-0.045em]">
            Move smarter.
            <span className="block text-[#fafaf7]/55">See every ride as it happens.</span>
          </p>
          <ul className="grid max-w-lg gap-6 border-t border-white/12 pt-8">
            {POINTS.map((point) => (
              <li key={point.title} className="grid grid-cols-[10.5rem_1fr] gap-4 text-sm">
                <span className="font-medium">{point.title}</span>
                <span className="text-[#fafaf7]/60">{point.body}</span>
              </li>
            ))}
          </ul>
        </div>
      </aside>
    </div>
  );
}

/** A street grid with one route drawn across it, ending at a live position. Decorative only. */
function RouteArt() {
  return (
    <svg viewBox="0 0 600 700" preserveAspectRatio="xMidYMid slice" className="absolute inset-0 size-full">
      <defs>
        <pattern id="auth-grid" width="46" height="46" patternUnits="userSpaceOnUse" patternTransform="rotate(-12)">
          <path d="M46 0H0V46" fill="none" stroke="#fafaf7" strokeOpacity="0.06" strokeWidth="1" />
        </pattern>
        <linearGradient id="auth-fade" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0.35" stopColor="#121311" stopOpacity="0" />
          <stop offset="0.85" stopColor="#121311" stopOpacity="0.95" />
        </linearGradient>
      </defs>
      <rect width="600" height="700" fill="url(#auth-grid)" />
      <path d="M40 120 C 150 130, 170 60, 260 90 S 330 250, 420 230 S 520 150, 560 190" fill="none" stroke="#fafaf7"
        strokeOpacity="0.14" strokeWidth="10" strokeLinecap="round" />
      <path d="M40 120 C 150 130, 170 60, 260 90 S 330 250, 420 230" fill="none" stroke="#e5561f" strokeWidth="4"
        strokeLinecap="round" />
      <circle cx="40" cy="120" r="7" fill="#121311" stroke="#fafaf7" strokeWidth="3" />
      <circle cx="420" cy="230" r="22" fill="#e5561f" fillOpacity="0.22" />
      <circle cx="420" cy="230" r="8" fill="#e5561f" stroke="#121311" strokeWidth="3" />
      <rect x="552" y="182" width="16" height="16" rx="3" fill="#fafaf7" />
      <rect width="600" height="700" fill="url(#auth-fade)" />
    </svg>
  );
}
