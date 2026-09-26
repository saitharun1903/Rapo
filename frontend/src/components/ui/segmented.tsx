import clsx from "clsx";

type Option<T extends string> = { value: T; label: string };

/** A small set of mutually exclusive choices, exposed as a radio group. */
export function Segmented<T extends string>({ label, options, value, onChange }: {
  label: string;
  options: readonly Option<T>[];
  value: T;
  onChange: (value: T) => void;
}) {
  return (
    <div role="radiogroup" aria-label={label} className="inline-flex rounded-control bg-surface-2 p-0.5">
      {options.map((option) => (
        <button key={option.value} type="button" role="radio" aria-checked={option.value === value}
          onClick={() => onChange(option.value)}
          className={clsx("rounded-[8px] px-3 py-1.5 text-sm font-medium transition-colors",
            option.value === value ? "bg-surface text-fg shadow-raise" : "text-fg-muted hover:text-fg")}>
          {option.label}
        </button>
      ))}
    </div>
  );
}
