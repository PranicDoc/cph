import { cn } from "@/lib/utils";
import type { ReviewStatus } from "../../types/review";

const FILTERS: { value: ReviewStatus | ""; label: string }[] = [
  { value: "", label: "All" },
  { value: "PENDING_REVIEW", label: "In review" },
  { value: "CHANGES_REQUESTED", label: "Changes requested" },
  { value: "APPROVED", label: "Approved" },
  { value: "DISMISSED", label: "Dismissed" },
];

interface Props {
  value: ReviewStatus | "";
  onChange: (status: ReviewStatus | "") => void;
  /** The rows across every status, for the counts; absent until loaded. */
  items?: { status: ReviewStatus }[];
}

/** The segmented status filter of the reviewer-side lists. */
export default function StatusFilter({ value, onChange, items }: Props) {
  return (
    <div
      className="seg inline-flex max-w-full gap-0.5 self-start overflow-x-auto rounded-xl bg-muted p-1"
      role="group"
      aria-label="Filter by status"
    >
      {FILTERS.map((f) => {
        const count = items && (f.value ? items.filter((i) => i.status === f.value).length : items.length);
        return (
          <button
            key={f.value}
            type="button"
            aria-pressed={value === f.value}
            className={cn(
              "whitespace-nowrap rounded-lg px-3 py-1.5 text-sm font-medium max-[760px]:!flex-[1_0_auto]",
              value === f.value && "on bg-[var(--color-card)] shadow-sm"
            )}
            onClick={() => onChange(f.value)}
          >
            {f.label}
            {count !== undefined && " "}
            {count !== undefined && <span className="tabular-nums">{count}</span>}
          </button>
        );
      })}
    </div>
  );
}
