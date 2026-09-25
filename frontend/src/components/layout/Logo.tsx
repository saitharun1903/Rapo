import clsx from "clsx";

/** The RideFlow mark: an origin and a destination joined by a flowing route. */
export function Logo({ className, withWordmark = true }: { className?: string; withWordmark?: boolean }) {
  return (
    <span className={clsx("inline-flex items-center gap-2 font-bold tracking-tight text-fg", className)}>
      <svg viewBox="0 0 32 32" className="size-7" aria-hidden>
        <rect width="32" height="32" rx="9" className="fill-brand" />
        <path d="M9 22c3.5 0 4-12 7.5-12S20 22 23.5 22" fill="none" stroke="white" strokeWidth="2.6" strokeLinecap="round" />
        <circle cx="9" cy="22" r="2.6" fill="white" />
        <circle cx="23.5" cy="10" r="2.6" className="fill-accent" stroke="white" strokeWidth="1.4" />
      </svg>
      {withWordmark && <span className="text-lg">RideFlow</span>}
    </span>
  );
}
