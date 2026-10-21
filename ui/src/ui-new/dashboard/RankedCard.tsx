import { useState } from "react";
import { ChartPeriodPicker } from "../../components/ChartPeriodPicker";
import { useTopProtocols } from "../../hooks/useTopProtocols";
import { useTopSymptoms } from "../../hooks/useTopSymptoms";
import type { ChartPeriod } from "../../types/chart";

/** Bar width as a percentage of the largest count (the top row is always full width). */
export function barPercent(count: number, max: number): number {
  if (max <= 0 || count <= 0) return 0;
  return Math.max(4, Math.round((count / max) * 100));
}

interface Row {
  label: string;
  count: number;
}

function Card({ title, rows, isLoading, isError, onRetry, period, onPeriod }: {
  title: string;
  rows: Row[] | undefined;
  isLoading: boolean;
  isError: boolean;
  onRetry: () => void;
  period: ChartPeriod;
  onPeriod: (p: ChartPeriod) => void;
}) {
  const max = Math.max(0, ...(rows ?? []).map((r) => r.count));
  return (
    <div className="surface card">
      <div className="card-h">
        <h2>{title}</h2>
        <ChartPeriodPicker value={period} onChange={onPeriod} />
      </div>
      {isLoading && (
        <div aria-hidden="true">
          {[0, 1, 2, 3].map((i) => (
            <div key={i} className="skel" style={{ height: 14, marginTop: 14 }} />
          ))}
        </div>
      )}
      {!isLoading && isError && (
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12 }}>
          <p className="err" style={{ margin: 0 }}>Unable to load data — try refreshing.</p>
          <button className="btn btn-secondary" type="button" onClick={onRetry}>Retry</button>
        </div>
      )}
      {!isLoading && !isError && rows?.length === 0 && <p className="muted" style={{ padding: "24px 0", textAlign: "center" }}>No data for this period</p>}
      {!isLoading && !isError && rows && rows.length > 0 &&
        rows.map((r) => (
          <div key={r.label} className="hbar" title={`${r.label}: ${r.count}`}>
            <span style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{r.label}</span>
            <i><b style={{ width: `${barPercent(r.count, max)}%` }} /></i>
            <span className="num muted">{r.count}</span>
          </div>
        ))}
    </div>
  );
}

export function TopProtocolsList({ healerId, initialPeriod }: { healerId: string; initialPeriod?: ChartPeriod }) {
  const [period, setPeriod] = useState<ChartPeriod>(initialPeriod ?? { period: "30d" });
  const q = useTopProtocols(healerId, period);
  return <Card title="Top protocols" rows={q.data?.map((d) => ({ label: d.protocol, count: d.count }))} isLoading={q.isLoading} isError={q.isError} onRetry={() => q.refetch()} period={period} onPeriod={setPeriod} />;
}

export function TopSymptomsList({ healerId, initialPeriod }: { healerId: string; initialPeriod?: ChartPeriod }) {
  const [period, setPeriod] = useState<ChartPeriod>(initialPeriod ?? { period: "30d" });
  const q = useTopSymptoms(healerId, period);
  return <Card title="Top symptoms" rows={q.data?.map((d) => ({ label: d.symptom, count: d.count }))} isLoading={q.isLoading} isError={q.isError} onRetry={() => q.refetch()} period={period} onPeriod={setPeriod} />;
}
