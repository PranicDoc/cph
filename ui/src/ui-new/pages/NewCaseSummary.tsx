import { useState } from "react";
import type { ReactNode } from "react";
import { Activity, CalendarDays, ChevronDown, FileText, Info, Package, Sparkles, User } from "lucide-react";
import { exerciseLabels } from "../../lib/exercise";
import {
  LabFindingsList,
  formatDate,
  formatTime,
  yesNo,
  type CaseSummaryData,
  type ChakraScan,
  type HealingSession,
} from "../../pages/CaseSummary";

function CardTitle({ icon, children, right }: { icon: ReactNode; children: ReactNode; right?: ReactNode }) {
  return (
    <div className="card-h">
      <h2 className="hl-trunc-row" style={{ display: "flex", alignItems: "center", gap: 10 }}>
        <span className="avatar" style={{ width: 30, height: 30, borderRadius: 10, flex: "none" }} aria-hidden="true">{icon}</span>
        <span className="hl-trunc">{children}</span>
      </h2>
      {right}
    </div>
  );
}

function Item({ label, children, wide, truncate }: { label: string; children: ReactNode; wide?: boolean; truncate?: boolean }) {
  return (
    <div style={{ minWidth: 0, ...(wide ? { gridColumn: "1 / -1" } : {}) }}>
      <div className="small muted" style={{ fontWeight: 550, marginBottom: 4 }}>{label}</div>
      <div className={truncate ? "hl-trunc" : undefined} title={truncate && typeof children === "string" ? children : undefined}>{children ?? "—"}</div>
    </div>
  );
}

const when = (m: { morningDose?: boolean; afternoonDose?: boolean; nightDose?: boolean }) =>
  [m.morningDose && "Morning", m.afternoonDose && "Afternoon", m.nightDose && "Night"].filter(Boolean).join(", ") || "—";

function ScanTable({ title, scan }: { title: string; scan?: ChakraScan }) {
  return (
    <div>
      <div className="small muted" style={{ fontWeight: 600, marginBottom: 8 }}>{title}</div>
      {scan && scan.readings.length > 0 ? (
        <div className="t-wrap">
          <table className="t">
            <thead><tr><th>Chakra</th><th>Energy</th><th>Activation</th></tr></thead>
            <tbody>
              {scan.readings.map((r) => (
                <tr key={r.id}>
                  <td data-label="Chakra"><span className="name">{r.chakraName}</span></td>
                  <td data-label="Energy">{r.energyLevel || "—"}</td>
                  <td data-label="Activation">{r.activationLevel || "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <p className="small muted">Not recorded</p>
      )}
    </div>
  );
}

function SessionBlock({ session, data, open, onToggle }: { session: HealingSession; data: CaseSummaryData; open: boolean; onToggle: () => void }) {
  const pre = session.scans.find((s) => s.scanType === "PRE");
  const post = session.scans.find((s) => s.scanType === "POST");
  return (
    <div style={{ borderTop: "1px solid var(--hair)", padding: "14px 0" }}>
      <button type="button" aria-expanded={open} onClick={onToggle} style={{ all: "unset", cursor: "pointer", display: "flex", width: "100%", alignItems: "center", justifyContent: "space-between", gap: 12 }}>
        <span className="hl-trunc" style={{ fontWeight: 600 }}>
          Session {session.sessionNumber}
          <span className="small muted" style={{ fontWeight: 400 }}> · {formatDate(session.sessionDate)}{session.startTime ? `, ${formatTime(session.startTime)}–${formatTime(session.endTime)}` : ""}</span>
        </span>
        <span style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <span className={`badge ${session.status === "COMPLETED" ? "ok" : "off"}`}>{session.status === "COMPLETED" ? "Completed" : session.status}</span>
          <ChevronDown className="ico" aria-hidden="true" style={{ color: "var(--muted)", transform: open ? "rotate(180deg)" : undefined }} />
        </span>
      </button>
      {open && (
        <>
          <div className="row2" style={{ marginTop: 14, gap: 18 }}>
            <ScanTable title="Pre scan" scan={pre} />
            <ScanTable title="Post scan" scan={post} />
          </div>
          {session.symptomFeedbacks.length > 0 && (
            <div style={{ marginTop: 14 }}>
              <div className="small muted" style={{ fontWeight: 600, marginBottom: 8 }}>Symptom feedback</div>
              <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                {session.symptomFeedbacks.map((sf) => (
                  <span key={sf.id} className="chip">
                    {data.symptoms.find((s) => s.id === sf.caseSymptomId)?.symptomName ?? "Symptom"}: <strong>{sf.rating}/10</strong>
                  </span>
                ))}
              </div>
            </div>
          )}
          {session.qualitativeFeedbacks.map((qf) => (
            <div key={qf.id} className="cpanel" style={{ marginTop: 12 }}>
              <span className="badge">{qf.feedbackType === "FINAL" ? "Final" : "Interim"} feedback</span>
              {qf.feedbackText && <p style={{ marginTop: 8 }}>{qf.feedbackText}</p>}
              {qf.patientRemarks && <p className="small muted" style={{ marginTop: 4 }}>Patient: {qf.patientRemarks}</p>}
            </div>
          ))}
        </>
      )}
    </div>
  );
}

/** New-view case summary laid out as in the CaseSummary mockup: details on the left, status and closure on the right. */
export default function NewCaseSummary({ data, onBack }: { data: CaseSummaryData; onBack: () => void }) {
  const sessions = [...data.sessions].sort((a, b) => b.sessionNumber - a.sessionNumber);
  const [open, setOpen] = useState<Set<string>>(() => new Set(sessions[0] ? [sessions[0].id] : []));
  const toggle = (id: string) => setOpen((prev) => { const n = new Set(prev); if (n.has(id)) n.delete(id); else n.add(id); return n; });
  const name = `${data.patientFirstName} ${data.patientLastName}`;
  const snap = data.medicalSnapshot;
  const closed = !!data.closure?.closureDate;
  const pages = [
    data.basicBookPg && `Basic book p. ${data.basicBookPg}`,
    data.advancedBookPg && `Advanced book p. ${data.advancedBookPg}`,
    data.psychotherapyPg && `Psychotherapy p. ${data.psychotherapyPg}`,
  ].filter(Boolean) as string[];

  return (
    <div className="hlx">
      <div className="page-head">
        <div style={{ minWidth: 0 }}>
          <h1>Case summary</h1>
          <p className="muted hl-trunc" style={{ marginTop: 6 }} title={name}>
            {name} · Opened {formatDate(data.caseCreatedAt)}
            {closed && ` · Closed ${formatDate(data.closure?.closureDate)}`}
          </p>
        </div>
        <button className="btn btn-secondary" type="button" onClick={onBack}>Back to patients</button>
      </div>

      <div className="grid2" style={{ alignItems: "start" }}>
        <div className="stack">
          <div className="surface card">
            <CardTitle icon={<User style={{ width: 16, height: 16 }} />}>Patient information</CardTitle>
            <div className="row2" style={{ gap: "18px 24px" }}>
              <Item label="Full name" truncate>{name}</Item>
              <Item label="Date of birth">{formatDate(data.dob)}</Item>
              <Item label="Gender">{data.gender || "—"}</Item>
              <Item label="Marital status">{data.maritalStatus || "—"}</Item>
              <Item label="Contact">{data.contact || "—"}</Item>
              <Item label="Occupation">{data.occupation || "—"}</Item>
            </div>
          </div>

          <div className="surface card">
            <CardTitle icon={<FileText style={{ width: 16, height: 16 }} />}>Case details</CardTitle>
            <div className="row2" style={{ gap: "18px 24px" }}>
              <Item label="Medical condition" wide>{data.medicalCondition || "—"}</Item>
              <Item label="Complementary treatments">
                {yesNo(data.complementaryTreatments)}
                {data.complementaryTreatments && data.complementaryDetails ? `: ${data.complementaryDetails}` : ""}
              </Item>
              <Item label="Infectious illness">
                {yesNo(data.infectiousIllness)}
                {data.infectiousIllness && data.infectiousDetails ? `: ${data.infectiousDetails}` : ""}
              </Item>
              {pages.length > 0 && (
                <Item label="Book pages" wide>
                  <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                    {pages.map((p) => <span key={p} className="badge">{p}</span>)}
                  </div>
                </Item>
              )}
              {data.labFindings && data.labFindings.length > 0 && (
                <Item label="Lab findings" wide><LabFindingsList keys={data.labFindings} /></Item>
              )}
            </div>
          </div>

          {snap && (
            <div className="surface card">
              <CardTitle icon={<Sparkles style={{ width: 16, height: 16 }} />}>Medical history (at case start)</CardTitle>
              <div className="row2" style={{ gap: "18px 24px" }}>
                <Item label="General health">{snap.generalHealth || "—"}</Item>
                <Item label="Diet">{snap.diet || "—"}</Item>
                <Item label="Exercise">
                  {exerciseLabels(snap.exercise).join(", ") || (snap.exercise ? "None" : "—")}
                  {exerciseLabels(snap.exercise).length > 0 && snap.exerciseFrequency ? `, ${snap.exerciseFrequency}` : ""}
                </Item>
                <Item label="Hypertension">{yesNo(snap.hypertension)}</Item>
                <Item label="GI issues">{yesNo(snap.giIssues)}</Item>
                <Item label="Stress / anxiety">{yesNo(snap.stressDepressionAnxiety)}</Item>
                <Item label="Pregnant">{yesNo(snap.pregnant)}</Item>
                <Item label="Cancer">
                  {yesNo(snap.cancer)}
                  {snap.cancer && snap.cancerLocation ? `: ${snap.cancerLocation}` : ""}
                </Item>
                {snap.cancer && snap.cancerDetails && <Item label="Cancer details" wide>{snap.cancerDetails}</Item>}
                {snap.additionalNotes && <Item label="Additional notes" wide>{snap.additionalNotes}</Item>}
              </div>
            </div>
          )}

          {data.symptoms.length > 0 && (
            <div className="surface card">
              <CardTitle icon={<Info style={{ width: 16, height: 16 }} />}>Symptoms</CardTitle>
              <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                {data.symptoms.map((s) => (
                  <span key={s.id} className="chip" title={s.description || undefined}>
                    {s.symptomName}
                    {s.resolved && <span className="badge ok" style={{ height: 20 }}>Resolved</span>}
                  </span>
                ))}
              </div>
            </div>
          )}

          {data.medications.length > 0 && (
            <div className="surface card">
              <CardTitle icon={<Package style={{ width: 16, height: 16 }} />}>Medications</CardTitle>
              <div className="t-wrap">
                <table className="t">
                  <thead><tr><th>Medicine</th><th>Dosage</th><th>When</th></tr></thead>
                  <tbody>
                    {data.medications.map((m) => (
                      <tr key={m.id}>
                        <td data-label="Medicine"><span className="name">{m.medicationName}</span></td>
                        <td data-label="Dosage">{m.dosage || "—"}</td>
                        <td data-label="When">{when(m)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {sessions.length > 0 && (
            <div className="surface card">
              <CardTitle icon={<CalendarDays style={{ width: 16, height: 16 }} />}>Sessions ({sessions.length})</CardTitle>
              {sessions.map((s) => (
                <SessionBlock key={s.id} session={s} data={data} open={open.has(s.id)} onToggle={() => toggle(s.id)} />
              ))}
            </div>
          )}
        </div>

        <div className="stack stk">
          <div className="surface card">
            <div className="card-h">
              <h2>Case status</h2>
              <span className={`badge ${closed ? "off" : "ok"}`}><span className="dot" />{closed ? "Closed" : "Open"}</span>
            </div>
            <div className="row2" style={{ gap: "18px 24px" }}>
              <Item label="Opened">{formatDate(data.caseCreatedAt)}</Item>
              <Item label="Closed">{formatDate(data.closure?.closureDate)}</Item>
              <Item label="Total healings">{data.closure?.healingCount ?? data.sessions.length}</Item>
              <Item label="Healing continued">{yesNo(data.closure?.isHealingContinued)}</Item>
            </div>
          </div>
          {data.closure && (
            <div className="surface card">
              <div className="card-h"><h2>Case closure</h2></div>
              <Item label="Last feedback">{formatDate(data.closure.lastFeedbackDate)}</Item>
              {data.closure.additionalComments && (
                <div style={{ marginTop: 12 }}><Item label="Additional comments">{data.closure.additionalComments}</Item></div>
              )}
            </div>
          )}
          <div className="surface card">
            <button className="btn btn-secondary" type="button" disabled style={{ width: "100%", opacity: 0.7 }}>
              <Activity className="ico" /> Generate PDF <span className="badge off" style={{ height: 20 }}>Coming soon</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
