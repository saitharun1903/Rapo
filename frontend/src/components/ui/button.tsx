import clsx from "clsx";
import { Loader2 } from "lucide-react";
import Link from "next/link";
import { forwardRef, type ButtonHTMLAttributes, type ComponentProps, type ReactNode } from "react";

type Variant = "primary" | "signal" | "secondary" | "ghost" | "danger";
type Size = "sm" | "md" | "lg";

/**
 * primary: ink, the one main action on a screen. signal: the brand colour, kept for the few actions that start
 * something live (requesting a ride, going online). secondary and ghost for everything else.
 */
const VARIANTS: Record<Variant, string> = {
  primary: "bg-ink text-ink-fg hover:opacity-90",
  signal: "bg-brand text-brand-fg hover:brightness-105",
  secondary: "bg-surface text-fg border border-line-strong hover:bg-surface-2",
  ghost: "text-fg hover:bg-surface-2",
  danger: "bg-danger text-white hover:opacity-90",
};

const SIZES: Record<Size, string> = {
  sm: "h-8 px-3 text-sm gap-1.5",
  md: "h-10 px-4 text-sm gap-2",
  lg: "h-12 px-5 text-[0.95rem] gap-2",
};

export function buttonClasses(variant: Variant = "primary", size: Size = "md", className?: string): string {
  return clsx(
    "inline-flex select-none items-center justify-center whitespace-nowrap rounded-control font-medium",
    "transition-[background-color,opacity,transform,filter] duration-150 active:scale-[0.98]",
    "disabled:pointer-events-none disabled:opacity-45",
    VARIANTS[variant], SIZES[size], className,
  );
}

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: Variant;
  size?: Size;
  /** Shows a spinner and disables the button while an action runs. */
  loading?: boolean;
};

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant, size, loading = false, className, children, disabled, type = "button", ...rest }, ref,
) {
  return (
    <button ref={ref} type={type} className={buttonClasses(variant, size, className)} disabled={disabled || loading}
      aria-busy={loading || undefined} {...rest}>
      {loading && <Loader2 className="size-4 animate-spin" aria-hidden />}
      {children}
    </button>
  );
});

export function ButtonLink({ variant, size, className, ...rest }: ComponentProps<typeof Link> & { variant?: Variant; size?: Size }) {
  return <Link className={buttonClasses(variant, size, className)} {...rest} />;
}

type IconButtonProps = Omit<ButtonHTMLAttributes<HTMLButtonElement>, "children"> & {
  /** Read by screen readers; icon buttons have no visible text. */
  label: string;
  icon: ReactNode;
  tone?: "plain" | "raised";
};

/** A square button with only an icon: toolbars, map controls, sheet headers. */
export const IconButton = forwardRef<HTMLButtonElement, IconButtonProps>(function IconButton(
  { label, icon, tone = "plain", className, type = "button", ...rest }, ref,
) {
  return (
    <button ref={ref} type={type} aria-label={label} title={label}
      className={clsx(
        "inline-flex size-10 shrink-0 items-center justify-center rounded-control text-fg-muted transition-colors",
        "hover:bg-surface-2 hover:text-fg active:scale-[0.96] disabled:pointer-events-none disabled:opacity-45",
        tone === "raised" && "bg-surface text-fg shadow-raise",
        className,
      )}
      {...rest}>
      {icon}
    </button>
  );
});
