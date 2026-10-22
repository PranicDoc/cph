const BANDS = [
  { max: 0, label: "None", tone: "ok" },
  { max: 3, label: "Mild", tone: "ok" },
  { max: 6, label: "Moderate", tone: "warn" },
  { max: 8, label: "Severe", tone: "bad" },
  { max: 10, label: "Worst", tone: "bad" },
];

export function severityBand(value: number) {
  return BANDS.find((b) => value <= b.max) ?? BANDS[BANDS.length - 1];
}

/** Stored rating (0 to 10, higher is better, as the reports read it) for a severity (0 to 10, higher is worse). */
export function severityToRating(severity: number): number {
  return 10 - Math.min(10, Math.max(0, Math.round(severity)));
}

/**
 * 0 to 10 severity slider for the patient feedback page. A real range input sits over the drawn
 * track, so keyboard and screen-reader use work. Until the patient touches it, the thumb is hollow.
 */
export function SeveritySlider({ label, value, onChange }: { label: string; value: number | undefined; onChange: (v: number) => void }) {
  const rated = value !== undefined;
  const v = value ?? 0;
  const band = severityBand(v);
  const pct = Math.max(v * 10, 1);
  return (
    <div className="sl">
      <div style={{ display: "flex", justifyContent: "flex-end", minHeight: 24 }}>
        {rated ? <span className={`badge ${band.tone}`}>{v} · {band.label}</span> : <span className="small muted">Not rated yet</span>}
      </div>
      <div className="sl-track" style={{ ["--p" as string]: pct }}>
        {rated && <div className="sl-fill" style={{ width: `${v * 10}%` }} />}
        <div className={`sl-thumb${rated ? "" : " off"}`} style={{ left: `${v * 10}%` }} aria-hidden="true">
          {rated ? v : ""}
        </div>
        <input
          type="range"
          min={0}
          max={10}
          step={1}
          value={v}
          aria-label={`${label}: severity from 0 (none) to 10 (worst)`}
          aria-valuetext={rated ? `${v}, ${band.label}` : "Not rated yet"}
          onChange={(e) => onChange(Number(e.target.value))}
          onClick={(e) => onChange(Number((e.target as HTMLInputElement).value))}
          style={{ position: "absolute", inset: "-14px 0", width: "100%", height: 40, margin: 0, opacity: 0, cursor: "pointer" }}
        />
      </div>
      <div className="sl-ticks small muted" style={{ display: "flex", justifyContent: "space-between" }}>
        <span>0 None</span>
        <span>10 Worst</span>
      </div>
    </div>
  );
}
