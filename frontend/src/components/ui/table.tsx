import clsx from "clsx";
import type { HTMLAttributes, TdHTMLAttributes, ThHTMLAttributes } from "react";

export function Table({ caption, className, children }: { caption: string; className?: string; children: React.ReactNode }) {
  return (
    <div className={clsx("overflow-x-auto rounded-2xl border border-line bg-surface", className)}>
      <table className="w-full min-w-max text-sm">
        <caption className="sr-only">{caption}</caption>
        {children}
      </table>
    </div>
  );
}

export function Th({ className, ...rest }: ThHTMLAttributes<HTMLTableCellElement>) {
  return <th scope="col" className={clsx("border-b border-line bg-surface-2 px-4 py-2.5 text-left text-xs font-semibold uppercase tracking-wide text-fg-muted", className)} {...rest} />;
}

export function Td({ className, ...rest }: TdHTMLAttributes<HTMLTableCellElement>) {
  return <td className={clsx("border-b border-line px-4 py-3 align-middle text-fg", className)} {...rest} />;
}

export function Tr({ className, ...rest }: HTMLAttributes<HTMLTableRowElement>) {
  return <tr className={clsx("last:[&>td]:border-0 hover:bg-surface-2/60", className)} {...rest} />;
}
