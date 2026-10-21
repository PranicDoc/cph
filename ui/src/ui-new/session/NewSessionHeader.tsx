import { usePatient } from "../../context/PatientContext";
import { HealingSession } from "../../interfaces";
import { useSessionSummary } from "../../pages/useSessionSummary";

const STATUS_TONE: Record<string, string> = {
  DRAFT: "warn",
  ACTIVE: "ok",
  CONTINUED: "warn",
  CLOSED: "off",
};

function initials(first?: string, last?: string) {
  return `${first?.[0] ?? ""}${last?.[0] ?? ""}`.toUpperCase() || "?";
}

/** New-view patient banner for the session screens: same data as the classic header, laid out as one card. */
export default function NewSessionHeader({ healingSession }: { healingSession: HealingSession }) {
  const { selectedPatient } = usePatient();
  const { conditions, sessionCount, lastVisit } = useSessionSummary(healingSession);
  const chips = conditions ? conditions.split(" | ") : [];
  const status = healingSession?.status;

  return (
    <section className="hl-banner hl-surface" aria-label="Patient">
      <div className="hl-banner-avatar" aria-hidden="true">
        {initials(selectedPatient?.firstName, selectedPatient?.lastName)}
      </div>
      <div className="hl-banner-main">
        <h1 className="hl-banner-title">
          Healing session, {selectedPatient?.firstName} {selectedPatient?.lastName}
        </h1>
        <p className="hl-banner-sub">
          <span>Age {selectedPatient?.age}</span>
          <span>{selectedPatient?.gender}</span>
          <span>Session {sessionCount}</span>
          {lastVisit && <span>Last visit {lastVisit} ago</span>}
        </p>
        {chips.length > 0 && (
          <ul className="hl-banner-chips" aria-label="Conditions">
            {chips.map((c) => (
              <li key={c} className="hl-chip">
                {c}
              </li>
            ))}
          </ul>
        )}
      </div>
      {status && <span className={`hl-badge hl-badge-${STATUS_TONE[status] ?? "off"}`}>{status}</span>}
    </section>
  );
}
