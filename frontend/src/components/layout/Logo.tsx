import clsx from "clsx";

/**
 * The Raido mark: a route arriving at a live position. The wordmark dots its "i" with the same signal colour, the
 * colour Raido uses for everything that is live.
 */
export function Logo({ className, withWordmark = true, wordmarkClassName }: { className?: string; withWordmark?: boolean; wordmarkClassName?: string }) {
  return (
    <span className={clsx("inline-flex items-center gap-2 text-fg", className)}>
      <svg viewBox="0 0 32 32" className="size-7 shrink-0" aria-hidden>
        <rect width="32" height="32" rx="8" className="fill-ink" />
        <path d="M7.5 23.5c5 0 5.5-11 12.5-11" fill="none" className="stroke-ink-fg" strokeWidth="2.6" strokeLinecap="round" />
        <circle cx="21.5" cy="12.5" r="7" className="fill-brand" fillOpacity="0.28" />
        <circle cx="21.5" cy="12.5" r="3.6" className="fill-brand" />
      </svg>
      {withWordmark && <Wordmark className={wordmarkClassName} />}
    </span>
  );
}

function Wordmark({ className }: { className?: string }) {
  return (
    <span className={clsx("text-[1.3rem] font-semibold leading-none tracking-[-0.045em]", className)} aria-label="Raido">
      <span aria-hidden>
        ra
        <span className="relative inline-block">
          {"ı"}
          <span className="absolute left-1/2 top-[0.02em] size-[0.2em] -translate-x-1/2 rounded-full bg-brand" />
        </span>
        do
      </span>
    </span>
  );
}
