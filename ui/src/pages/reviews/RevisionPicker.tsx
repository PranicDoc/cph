import type { RevisionResponse } from "../../types/review";

interface Props {
  revisions: RevisionResponse[];
  latestRevisionNo: number;
  value: number;
  onChange: (revisionNo: number) => void;
}

/** Chooses which revision's PDF is on screen; plain text while there is only one. */
export default function RevisionPicker({ revisions, latestRevisionNo, value, onChange }: Props) {
  if (revisions.length === 0) return null;
  if (revisions.length === 1) return <span className="muted text-sm">Revision R{latestRevisionNo}, latest</span>;
  return (
    <label className="flex items-center gap-2 text-sm">
      <span className="muted">Revision</span>
      <select
        className="h-9 rounded-lg border bg-[var(--color-card)] px-2 text-foreground"
        value={value}
        onChange={(e) => onChange(Number(e.target.value))}
      >
        {revisions.map((r) => (
          <option key={r.revisionNo} value={r.revisionNo}>
            {r.revisionNo === latestRevisionNo ? `R${r.revisionNo}, latest` : `R${r.revisionNo}`}
          </option>
        ))}
      </select>
    </label>
  );
}
