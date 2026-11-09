import type { ReactNode } from "react";

export interface MetricProps {
  label: string;
  value: ReactNode;
  note?: string;
  accent?: boolean;
}

/** Displays a labeled value, optionally emphasizing an algorithm metric. */
export function Metric({ label, value, note, accent = false }: MetricProps) {
  return (
    <div className={`metric${accent ? " metric-accent" : ""}`}>
      <span className="metric-label">{label}</span>
      <strong className="metric-value">{value}</strong>
      {note && <span className="metric-note">{note}</span>}
    </div>
  );
}
