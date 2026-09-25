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
    <div role="radiogroup" aria-label={label} className="inline-flex rounded-xl border border-line bg-surface-2 p-1">
      {options.map((option) => (
        <button key={option.value} type="button" role="radio" aria-checked={option.value === value}
          onClick={() => onChange(option.value)}
          className={clsx("rounded-lg px-3 py-1.5 text-sm font-medium transition-colors",
            option.value === value ? "bg-surface text-fg shadow-sm" : "text-fg-muted hover:text-fg")}>
          {option.label}
        </button>
      ))}
    </div>
  );
}
