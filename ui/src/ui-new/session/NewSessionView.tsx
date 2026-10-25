import { useState } from "react";
import { ChevronDown, Pencil } from "lucide-react";
import { LabFindingsView, MedicationsView } from "../../components/CaseViewWidgets";
import type { HealingSession } from "../../interfaces";
import type { Protocol } from "../../hooks/admin/useMasterData";
import { isLegacyProtocol } from "../../lib/protocolSelection";

interface Props {
  sessionData: HealingSession;
  protocols: Protocol[];
  onEdit: () => void;
  onEditSession: (session: any) => void;
  /** Present only once the case has a closure (CLOSED or CONTINUED). */
  onEditClosure?: () => void;
  onBack: () => void;
  onNext: () => void;
  nextLabel: string;
}

const label = { fontWeight: 550, marginBottom: 4 } as const;

function Field({ title, children, wide }: { title: string; children: React.ReactNode; wide?: boolean }) {
  return (
    <div style={wide ? { gridColumn: "1 / -1" } : undefined}>
      <div className="small muted" style={label}>{title}</div>
      <div>{children}</div>
    </div>
  );
}

/** Step 0 of a session in view mode, laid out as in the approved mockup (details and past sessions, next step on the right). */
export default function NewSessionView({ sessionData, protocols, onEdit, onEditSession, onEditClosure, onBack, onNext, nextLabel }: Props) {
  const [open, setOpen] = useState<Set<string>>(new Set());
  const toggle = (id: string) =>
    setOpen((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  const symptoms: string[] = (sessionData.symptoms ?? []).map((s: { symptomName: string }) => s.symptomName).filter(Boolean);
  const sessions = sessionData.healingSession ?? [];
  const hasProtocolText = !!sessionData.healingDetails?.protocolName;

  return (
    <div className="grid2" style={{ alignItems: "start" }}>
      <div className="stack">
        <div className="surface card">
          <div className="card-h">
            <h2>Patient medical details</h2>
            <button className="icon-btn" type="button" aria-label="Edit session" onClick={onEdit}>
              <Pencil className="ico" />
            </button>
          </div>
          <div className="row2" style={{ gap: 22 }}>
            <Field title="Chief complaint" wide>{sessionData.medicalCondition || "—"}</Field>
            <Field title="Symptoms">
              {symptoms.length ? (
                <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                  {symptoms.map((s) => (
                    <span key={s} className="chip">{s}</span>
                  ))}
                </div>
              ) : (
                "—"
              )}
            </Field>
            <Field title="Lab findings"><LabFindingsView keys={sessionData.labFindings ?? []} /></Field>
            <Field title="Medications" wide><MedicationsView medications={sessionData.medications ?? []} /></Field>
            <Field title="Complementary details">{sessionData.complementaryDetails || "—"}</Field>
            <Field title="Infectious illness">{(sessionData.infectiousIllness as unknown as string) || "—"}</Field>
            <Field title="Protocols" wide>
              {protocols.length ? (
                <div style={{ marginTop: -10 }}>
                  {protocols.map((p) => (
                    <div key={p.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12, padding: "10px 0", borderTop: "1px solid var(--hair)" }}>
                      <div style={{ minWidth: 0 }}>
                        <div style={{ fontWeight: 600 }}>{p.name}</div>
                        {!isLegacyProtocol(p) && p.sourceBook && (
                          <div className="small muted" style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{p.sourceBook}</div>
                        )}
                      </div>
                      {!isLegacyProtocol(p) && p.pageNumber ? <span className="badge" style={{ flex: "none" }}>p. {p.pageNumber}</span> : null}
                    </div>
                  ))}
                </div>
              ) : hasProtocolText ? (
                sessionData.healingDetails?.protocolName
              ) : (
                "—"
              )}
            </Field>
            {(sessionData.basicBookPg || sessionData.advancedBookPg || sessionData.psychotherapyPg) && (
              <Field title="Book pages" wide>
                <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                  {sessionData.basicBookPg && <span className="chip">Basic: {sessionData.basicBookPg}</span>}
                  {sessionData.advancedBookPg && <span className="chip">Advanced: {sessionData.advancedBookPg}</span>}
                  {sessionData.psychotherapyPg && <span className="chip">Psychotherapy: {sessionData.psychotherapyPg}</span>}
                </div>
              </Field>
            )}
          </div>
        </div>

        {sessions.length > 0 && (
          <div className="surface card">
            <div className="card-h">
              <h2>Past sessions</h2>
              <span className="small muted">{sessions.length} session{sessions.length === 1 ? "" : "s"}</span>
            </div>
            {sessions.map((session) => {
              const id = String(session.id);
              const isOpen = open.has(id);
              return (
                <div key={id} style={{ borderTop: "1px solid var(--hair)", padding: "14px 0" }}>
                  <button
                    type="button"
                    aria-expanded={isOpen}
                    onClick={() => toggle(id)}
                    style={{ all: "unset", cursor: "pointer", display: "flex", width: "100%", alignItems: "center", justifyContent: "space-between" }}
                  >
                    <span style={{ fontWeight: 600 }}>Session {session.sessionNumber}</span>
                    <ChevronDown className="ico" style={{ color: "var(--muted)", transform: isOpen ? "rotate(180deg)" : undefined }} />
                  </button>
                  {isOpen && (
                    <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 10 }}>
                      <button className="btn btn-secondary" type="button" onClick={() => onEditSession(session)}>
                        <Pencil className="ico" />
                        Edit
                      </button>
                    </div>
                  )}
                  {isOpen && (
                    <div className="row2" style={{ marginTop: 14, gap: 18 }}>
                      {session.scans.map((scan) => (
                        <div key={scan.id}>
                          <div className="small muted" style={{ fontWeight: 600, marginBottom: 8 }}>{scan.scanType} scan</div>
                          <div className="t-wrap">
                            <table className="t">
                              <thead>
                                <tr><th>Chakra</th><th>Energy</th><th>Activation</th></tr>
                              </thead>
                              <tbody>
                                {scan.readings.map((r: { id?: string; chakraName: string; energyLevel?: string; activationLevel?: string }, i: number) => (
                                  <tr key={r.id ?? i}>
                                    <td data-label="Chakra"><span className="name">{r.chakraName}</span></td>
                                    <td data-label="Energy">{r.energyLevel ?? "—"}</td>
                                    <td data-label="Activation">{r.activationLevel ?? "—"}</td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>

      <div className="stack stk">
        <div className="surface card">
          <div className="card-h"><h2>Next step</h2></div>
          <button className="btn btn-primary" type="button" style={{ width: "100%", height: 44 }} onClick={onNext}>
            {nextLabel}
          </button>
          {onEditClosure && (
            <button className="btn btn-secondary" type="button" style={{ width: "100%", marginTop: 10 }} onClick={onEditClosure}>
              Edit closure
            </button>
          )}
          <button className="btn btn-secondary" type="button" style={{ width: "100%", marginTop: 10 }} onClick={onBack}>
            Back to patients
          </button>
        </div>
      </div>
    </div>
  );
}
