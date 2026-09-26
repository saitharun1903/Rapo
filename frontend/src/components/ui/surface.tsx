import clsx from "clsx";
import { AlertTriangle } from "lucide-react";
import type { HTMLAttributes, ReactNode } from "react";
import { Button } from "./button";
import { errorMessage } from "@/lib/api/errors";

/** A container that exists to group, not to decorate: a hairline and the surface colour, no shadow. */
export function Card({ className, ...rest }: HTMLAttributes<HTMLDivElement>) {
  return <div className={clsx("rounded-card border border-line bg-surface p-5", className)} {...rest} />;
}

export function CardTitle({ children, action }: { children: ReactNode; action?: ReactNode }) {
  return (
    <div className="mb-4 flex items-center justify-between gap-3">
      <h2 className="text-[0.95rem] font-semibold tracking-tight text-fg">{children}</h2>
      {action}
    </div>
  );
}

export type Tone = "neutral" | "brand" | "success" | "warning" | "danger" | "ink";

const TONES: Record<Tone, string> = {
  neutral: "bg-surface-2 text-fg-muted",
  brand: "bg-brand-soft text-brand-strong",
  success: "bg-success-soft text-success",
  warning: "bg-warning-soft text-warning",
  danger: "bg-danger-soft text-danger",
  ink: "bg-ink text-ink-fg",
};

export function Badge({ tone = "neutral", children, className }: { tone?: Tone; children: ReactNode; className?: string }) {
  return (
    <span className={clsx("inline-flex items-center gap-1 rounded-md px-1.5 py-0.5 text-xs font-medium", TONES[tone], className)}>
      {children}
    </span>
  );
}

export function Skeleton({ className }: { className?: string }) {
  return <div aria-hidden className={clsx("animate-pulse rounded-control bg-surface-2", className)} />;
}

/** A loading placeholder for a list or panel, announced once to assistive technology. */
export function LoadingBlock({ label = "Loading", rows = 3 }: { label?: string; rows?: number }) {
  return (
    <div role="status" aria-label={label} className="flex flex-col gap-2">
      {Array.from({ length: rows }, (_, index) => <Skeleton key={index} className="h-14" />)}
    </div>
  );
}

/** Nothing to show yet: says why, and what would change that. */
export function EmptyState({ title, children, action, icon, asPageHeading = false }: {
  title: string;
  children?: ReactNode;
  action?: ReactNode;
  icon?: ReactNode;
  /** When the empty state is all the page shows, its title is the page's heading. */
  asPageHeading?: boolean;
}) {
  const Title = asPageHeading ? "h1" : "p";
  return (
    <div className="flex flex-col items-start gap-2 rounded-card border border-dashed border-line-strong px-5 py-8">
      {icon && <span className="mb-1 text-fg-muted">{icon}</span>}
      <Title className="font-medium text-fg">{title}</Title>
      {children && <div className="max-w-prose text-sm text-fg-muted">{children}</div>}
      {action && <div className="mt-2">{action}</div>}
    </div>
  );
}

export function ErrorState({ error, onRetry, title = "Could not load this" }: { error: unknown; onRetry?: () => void; title?: string }) {
  return (
    <div role="alert" className="flex flex-col items-start gap-2 rounded-card border border-danger/25 bg-danger-soft px-5 py-6">
      <p className="flex items-center gap-2 font-medium text-fg">
        <AlertTriangle className="size-4 text-danger" aria-hidden /> {title}
      </p>
      <p className="max-w-prose text-sm text-fg-muted">{errorMessage(error)}</p>
      {onRetry && <Button variant="secondary" size="sm" className="mt-1" onClick={onRetry}>Try again</Button>}
    </div>
  );
}

/** A labelled figure. Numbers use tabular figures so columns of them line up. */
export function Stat({ label, value, detail, className }: { label: string; value: ReactNode; detail?: ReactNode; className?: string }) {
  return (
    <div className={clsx("flex flex-col gap-1 rounded-card border border-line bg-surface p-4", className)}>
      <span className="eyebrow">{label}</span>
      <span className="num text-[1.6rem] font-semibold leading-tight tracking-tight text-fg">{value}</span>
      {detail && <span className="text-xs text-fg-muted">{detail}</span>}
    </div>
  );
}

export function PageHeader({ title, description, action }: { title: string; description?: ReactNode; action?: ReactNode }) {
  return (
    <div className="mb-8 flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 className="text-[1.75rem] font-semibold leading-tight tracking-[-0.03em] text-fg">{title}</h1>
        {description && <p className="mt-1.5 max-w-prose text-sm text-fg-muted">{description}</p>}
      </div>
      {action}
    </div>
  );
}

/** A label and a value on one line, for trip facts and receipts. */
export function KeyValue({ label, children, className }: { label: string; children: ReactNode; className?: string }) {
  return (
    <div className={clsx("flex items-baseline justify-between gap-4 py-2 text-sm", className)}>
      <dt className="text-fg-muted">{label}</dt>
      <dd className="text-right font-medium text-fg">{children}</dd>
    </div>
  );
}
