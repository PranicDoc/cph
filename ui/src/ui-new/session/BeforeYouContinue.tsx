import { Check } from "lucide-react";
import { hasChiefComplaint, hasProtocol, type SessionStep0Values } from "../../pages/schemas/sessionValidation";

type Values = SessionStep0Values & { labFindings?: unknown[] | null };

/** Right-hand checklist on the edit screen: the same required fields the form already enforces. */
export function BeforeYouContinue({ values }: { values: Values }) {
  const items = [
    { label: "Chief complaint", done: hasChiefComplaint(values), required: true },
    { label: "At least one protocol", done: hasProtocol(values), required: true },
    { label: "Lab findings (optional)", done: (values.labFindings?.length ?? 0) > 0, required: false },
  ];
  return (
    <div className="surface card" aria-label="Before you continue">
      <div className="card-h">
        <h2>Before you continue</h2>
      </div>
      <ul style={{ listStyle: "none", margin: 0, padding: 0, display: "flex", flexDirection: "column", gap: 12 }}>
        {items.map((i) => (
          <li key={i.label} style={{ display: "flex", alignItems: "center", gap: 10 }}>
            <span className={`chk${i.done ? " on" : ""}`} aria-hidden="true">
              {i.done && <Check />}
            </span>
            <span>{i.label}</span>
            <span className="sr-only">{i.done ? "done" : i.required ? "required" : "optional"}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
