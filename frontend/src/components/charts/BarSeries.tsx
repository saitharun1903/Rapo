"use client";

import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";

export type SeriesPoint = { label: string; [series: string]: string | number };

type BarSeriesProps = {
  data: SeriesPoint[];
  bars: { key: string; name: string; color: string; stack?: string }[];
  /** Names the chart; screen readers get the same numbers as a table. */
  label: string;
  valueFormatter?: (value: number) => string;
};

const CHART_HEIGHT = 260;

/** A bar chart in the theme's colours; colours are passed as CSS variables, which SVG fills accept. */
export function BarSeries({ data, bars, label, valueFormatter }: BarSeriesProps) {
  return (
    <figure aria-label={label} className="w-full">
      <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
        <BarChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
          <CartesianGrid vertical={false} stroke="var(--line)" />
          <XAxis dataKey="label" tickLine={false} axisLine={false} tick={{ fill: "var(--fg-muted)", fontSize: 12 }} minTickGap={16} />
          <YAxis tickLine={false} axisLine={false} tick={{ fill: "var(--fg-muted)", fontSize: 12 }} width={56}
            tickFormatter={valueFormatter} allowDecimals={false} />
          <Tooltip cursor={{ fill: "var(--surface-2)" }}
            contentStyle={{ background: "var(--surface)", border: "1px solid var(--line)", borderRadius: 12, color: "var(--fg)" }}
            formatter={valueFormatter ? (value) => valueFormatter(Number(value)) : undefined} />
          {bars.map((bar) => (
            <Bar key={bar.key} dataKey={bar.key} name={bar.name} fill={bar.color} stackId={bar.stack} radius={[4, 4, 0, 0]} />
          ))}
        </BarChart>
      </ResponsiveContainer>
      <table className="sr-only">
        <caption>{label}</caption>
        <thead><tr><th scope="col">Period</th>{bars.map((bar) => <th key={bar.key} scope="col">{bar.name}</th>)}</tr></thead>
        <tbody>
          {data.map((point) => (
            <tr key={point.label}>
              <th scope="row">{point.label}</th>
              {bars.map((bar) => {
                const value = Number(point[bar.key]);
                return <td key={bar.key}>{valueFormatter ? valueFormatter(value) : value}</td>;
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </figure>
  );
}
